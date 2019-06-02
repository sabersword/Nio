package org.ypq.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class TomcatClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TomcatClient.class);

    public static void main(String[] args) throws IOException {
        int i = 0;
        Selector selector = Selector.open();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel socketChannel = null;
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress("127.0.0.1", 8080));
            socketChannel.register(selector, SelectionKey.OP_CONNECT);
            while (true) {
                if (selector.select() == 0) {
                    continue;
                }
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    if (key.isConnectable()) {
                        while (!socketChannel.finishConnect()) ;
                        socketChannel.configureBlocking(false);
                        socketChannel.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocateDirect(1024));
                        LOGGER.info("与远程服务器连接成功,使用本地端口{}", socketChannel.socket().getLocalPort());
                    }
                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buf = (ByteBuffer) key.attachment();
                        long readCount;
                        readCount = sc.read(buf);
                        while (readCount > 0) {
                            buf.flip();
                            while (buf.hasRemaining()) {
                                System.out.print((char) buf.get());
                            }
                            System.out.println();
                            buf.clear();
                            readCount = sc.read(buf);
                        }
                        // 远程服务器断开连接后会不停触发OP_READ,并收到-1代表End-Of-Stream
                        if (readCount == -1) {
                            LOGGER.info("远程服务器断开了与本机的连接,本机也进行断开");
                            sc.close();
                        }
                    }
                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
