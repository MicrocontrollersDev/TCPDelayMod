package com.github.microcontrollersdev.tcpdelaymod.mixin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetAddress;

@Mixin(NetworkManager.class)
public class NetworkManagerMixin {
    @Shadow
    public static final LazyLoadBase<? extends EventLoopGroup> CLIENT_EPOLL_EVENTLOOP = null;
    @Shadow
    public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = null;

    /**
     * @author Microcontrollers
     * @reason i am NOT figuring out how to target this properly
     */
    @SideOnly(Side.CLIENT)
    @Overwrite
    public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort, boolean useNativeTransport) {
        final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Class<? extends SocketChannel> oclass;
        LazyLoadBase<? extends EventLoopGroup> lazyloadbase;
        if (Epoll.isAvailable() && useNativeTransport) {
            oclass = EpollSocketChannel.class;
            lazyloadbase = CLIENT_EPOLL_EVENTLOOP;
        } else {
            oclass = NioSocketChannel.class;
            lazyloadbase = CLIENT_NIO_EVENTLOOP;
        }

        new Bootstrap()
                .group(lazyloadbase.getValue())
                .handler(
                        new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel p_initChannel_1_) throws Exception {
                                try {
                                    p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, false);
                                } catch (ChannelException var3) {
                                }

                                p_initChannel_1_.pipeline()
                                        .addLast("timeout", new ReadTimeoutHandler(30))
                                        .addLast("splitter", new MessageDeserializer2())
                                        .addLast("decoder", new MessageDeserializer(EnumPacketDirection.CLIENTBOUND))
                                        .addLast("prepender", new MessageSerializer2())
                                        .addLast("encoder", new MessageSerializer(EnumPacketDirection.SERVERBOUND))
                                        .addLast("packet_handler", networkmanager);
                            }
                        }
                )
                .channel(oclass)
                .connect(address, serverPort) // THIS APPARENTLY HAS 9 VULNERABILITIES ACCORDING TO INTELLIJ. BOOHOO.
                .syncUninterruptibly();
        return networkmanager;
    }
}
