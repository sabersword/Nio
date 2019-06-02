package org.ypq.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NioServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioServer.class);

    private static final int BUF_SIZE = 1024;
    private static final int PORT = 8080;
    private static final int TIMEOUT = 3000;

    private Selector selector;
    private ByteBuffer sendBuffer = ByteBuffer.allocate(1024);

    public static void main(String[] args) {
        new NioServer().selector();
    }

    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel ssChannel = (ServerSocketChannel) key.channel();
        SocketChannel sc = ssChannel.accept();
        sc.configureBlocking(false);
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUF_SIZE);
        sc.register(key.selector(), SelectionKey.OP_READ, buffer);
        LOGGER.info("accept客户端成功,客户端使用端口{}", sc.socket().getPort());
        writeHello(sc);
    }

    public void writeHello(SocketChannel sc) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUF_SIZE);
        buffer.clear();
        buffer.put("welcome to server !".getBytes());
        buffer.flip();
        while (buffer.hasRemaining()) {
            sc.write(buffer);
        }
        buffer.clear();
    }

    public void handleRead(SelectionKey key) {
        SocketChannel sc = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();
        try {
            long bytesRead = sc.read(buf);
            StringBuffer sb = new StringBuffer();
            while (bytesRead > 0) {
                buf.flip();
                while (buf.hasRemaining()) {
                    sb.append((char) buf.get());
                }
                buf.clear();
                bytesRead = sc.read(buf);
            }
            LOGGER.info("收到客户端的消息:{}", sb.toString());
            writeResponse(sc, sb.toString());
            if (sb.toString().contains("3")) {
                sc.close();
            }
        } catch (IOException e) {
            key.cancel();
            e.printStackTrace();
            LOGGER.info("疑似一个客户端断开连接");
            try {
                sc.close();
            } catch (IOException e1) {
                LOGGER.info("SocketChannel 关闭异常");
            }
        }
    }

    public void writeResponse(SocketChannel sc, String request) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        buffer.clear();
        buffer.put(("response your answer!" + request).getBytes());
        buffer.flip();
        while (buffer.hasRemaining()) {
            sc.write(buffer);
        }
        buffer.compact();
    }

    public void selector() {
        ServerSocketChannel ssc = null;
        try {
            selector = Selector.open();
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(PORT));
            ssc.configureBlocking(false);
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            while (true) {
                if (selector.select(TIMEOUT) == 0) {
                    continue;
                }
                Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    }
                    else if (key.isReadable()) {
                        handleRead(key);
                    }
                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (selector != null) {
                    selector.close();
                }
                if (ssc != null) {
                    ssc.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

