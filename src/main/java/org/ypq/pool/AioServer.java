package org.ypq.pool;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AioServer {

    private static final Object waitObject = new Object();

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        /*
         * 对于使用的线程池技术，我一定要多说几句
         * 1、Executors是线程池生成工具，通过这个工具我们可以很轻松的生成“固定大小的线程池”、“调度池”、“可伸缩线程数量的池”。具体请看API Doc
         * 2、当然您也可以通过ThreadPoolExecutor直接生成池。
         * 3、这个线程池是用来得到操作系统的“IO事件通知”的，不是用来进行“得到IO数据后的业务处理的”。要进行后者的操作，您可以再使用一个池（最好不要混用）
         * 4、您也可以不使用线程池（不推荐），如果决定不使用线程池，直接AsynchronousServerSocketChannel.open()就行了。
         * */
        ExecutorService threadPool = Executors.newFixedThreadPool(20);
        AsynchronousChannelGroup group = AsynchronousChannelGroup.withThreadPool(threadPool);
        final AsynchronousServerSocketChannel serverSocketChannel = AsynchronousServerSocketChannel.open(group);

        //设置要监听的端口“0.0.0.0”代表本机所有IP设备
        serverSocketChannel.bind(new InetSocketAddress("0.0.0.0", 8083));
        //为AsynchronousServerSocketChannel注册监听，注意只是为AsynchronousServerSocketChannel通道注册监听
        //并不包括为 随后客户端和服务器 socketchannel通道注册的监听
        serverSocketChannel.accept(null, new ServerSocketChannelHandle(serverSocketChannel));

        //等待，以便观察现象（这个和要讲解的原理本身没有任何关系，只是为了保证守护线程不会退出）
        synchronized(waitObject) {
            waitObject.wait();
        }
    }

    static class ServerSocketChannelHandle implements CompletionHandler<AsynchronousSocketChannel, Void> {

        private AsynchronousServerSocketChannel serverSocketChannel;

        /**
         * @param serverSocketChannel
         */
        public ServerSocketChannelHandle(AsynchronousServerSocketChannel serverSocketChannel) {
            this.serverSocketChannel = serverSocketChannel;
        }

        /**
         * 注意，我们分别观察 this、socketChannel、attachment三个对象的id。
         * 来观察不同客户端连接到达时，这三个对象的变化，以说明ServerSocketChannelHandle的监听模式
         */
        @Override
        public void completed(AsynchronousSocketChannel socketChannel, Void attachment) {
            System.out.println("completed(AsynchronousSocketChannel result, ByteBuffer attachment)");
            //每次都要重新注册监听（一次注册，一次响应），但是由于“文件状态标示符”是独享的，所以不需要担心有“漏掉的”事件
            this.serverSocketChannel.accept(attachment, this);

            //为这个新的socketChannel注册“read”事件，以便操作系统在收到数据并准备好后，主动通知应用程序
            //在这里，由于我们要将这个客户端多次传输的数据累加起来一起处理，所以我们将一个stringbuffer对象作为一个“附件”依附在这个channel上
            //
            ByteBuffer readBuffer = ByteBuffer.allocate(2550);
            socketChannel.read(readBuffer, new StringBuffer(), new SocketChannelReadHandle(socketChannel , readBuffer));
        }

        /* (non-Javadoc)
         * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable, java.lang.Object)
         */
        @Override
        public void failed(Throwable exc, Void attachment) {
            System.out.println("failed(Throwable exc, ByteBuffer attachment)");
        }
    }

    static class SocketChannelReadHandle implements CompletionHandler<Integer, StringBuffer> {

        private AsynchronousSocketChannel socketChannel;

        /**
         * 专门用于进行这个通道数据缓存操作的ByteBuffer<br>
         * 当然，您也可以作为CompletionHandler的attachment形式传入。<br>
         * 这是，在这段示例代码中，attachment被我们用来记录所有传送过来的Stringbuffer了。
         */
        private ByteBuffer byteBuffer;

        public SocketChannelReadHandle(AsynchronousSocketChannel socketChannel , ByteBuffer byteBuffer) {
            this.socketChannel = socketChannel;
            this.byteBuffer = byteBuffer;
        }

        /* (non-Javadoc)
         * @see java.nio.channels.CompletionHandler#completed(java.lang.Object, java.lang.Object)
         */
        @Override
        public void completed(Integer result, StringBuffer historyContext) {
            //如果条件成立，说明客户端主动终止了TCP套接字，这时服务端终止就可以了
            if(result == -1) {
                try {
                    System.out.println("客户端断开TCP");
                    this.socketChannel.close();
                } catch (IOException e) {
                    System.out.println(e);
                }
                return;
            }

            System.out.println("completed(Integer result, Void attachment) : 然后我们来取出通道中准备好的值");
            /*
             * 实际上，由于我们从Integer result知道了本次channel从操作系统获取数据总长度
             * 所以实际上，我们不需要切换成“读模式”的，但是为了保证编码的规范性，还是建议进行切换。
             *
             * 另外，无论是JAVA AIO框架还是JAVA NIO框架，都会出现“buffer的总容量”小于“当前从操作系统获取到的总数据量”，
             * 但区别是，JAVA AIO框架中，我们不需要专门考虑处理这样的情况，因为JAVA AIO框架已经帮我们做了处理（做成了多次通知）
             * */
            this.byteBuffer.flip();
            byte[] contexts = new byte[1024];
            this.byteBuffer.get(contexts, 0, result);
            this.byteBuffer.clear();
            try {
                String nowContent = new String(contexts , 0 , result , "UTF-8");
                historyContext.append(nowContent);
                System.out.println("================目前的传输结果：" + historyContext);
            } catch (UnsupportedEncodingException e) {
                System.out.println(e);
            }

            //如果条件成立，说明还没有接收到“结束标记”
            if(historyContext.indexOf("over") == -1) {
                return;
            }else{
                //清空已经读取的缓存，并从新切换为写状态(这里要注意clear()和capacity()两个方法的区别)
                this.byteBuffer.clear();
                System.out.println("客户端发来的信息======message : " + historyContext);

                //======================================================
                //          当然接受完成后，可以在这里正式处理业务了
                //======================================================

                //回发数据，并关闭channel
                ByteBuffer sendBuffer = null;
                try {
                    sendBuffer = ByteBuffer.wrap(URLEncoder.encode("你好客户端,这是服务器的返回数据", "UTF-8").getBytes());
                    socketChannel.write(sendBuffer);
                    socketChannel.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //=========================================================================
            //          和上篇文章的代码相同，我们以“over”符号作为客户端完整信息的标记
            //=========================================================================
            System.out.println("=======收到完整信息，开始处理业务=========");
            historyContext = new StringBuffer();

            //还要继续监听（一次监听一次通知）
            this.socketChannel.read(this.byteBuffer, historyContext, this);
        }

        /* (non-Javadoc)
         * @see java.nio.channels.CompletionHandler#failed(java.lang.Throwable, java.lang.Object)
         */
        @Override
        public void failed(Throwable exc, StringBuffer historyContext) {
            System.out.println("=====发现客户端异常关闭，服务器将关闭TCP通道");
            try {
                this.socketChannel.close();
            } catch (IOException e) {
                System.out.println(e);
            }
        }

    }

}
