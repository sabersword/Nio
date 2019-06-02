package org.ypq.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NioClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(NioClient.class);

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
                        LOGGER.info("与服务器连接成功,使用本地端口{}", socketChannel.socket().getLocalPort());
                    }
                    if (key.isReadable()) {
                        SocketChannel sc = (SocketChannel) key.channel();
                        ByteBuffer buf = (ByteBuffer) key.attachment();
                        long bytesRead;
                        try {
                            bytesRead = sc.read(buf);
                        } catch (IOException e) {
                            e.printStackTrace();
                            LOGGER.info("远程服务器断开了与本机的连接,本机也进行断开");
                            sc.close();
                            continue;
                        }
                        while (bytesRead > 0) {
                            buf.flip();
                            while (buf.hasRemaining()) {
                                System.out.print((char) buf.get());
                            }
                            System.out.println();
                            buf.clear();
                            bytesRead = sc.read(buf);
                        }
                        TimeUnit.SECONDS.sleep(2);
                        String info = "I'm " + i++ + "-th information from client";
                        buffer.clear();
                        buffer.put(info.getBytes());
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            sc.write(buffer);
                        }
                    }
                    iter.remove();
                }
            }
        } catch (IOException | InterruptedException e) {
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
