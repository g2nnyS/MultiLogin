package moe.caa.multilogin.bukkit.injector.proxy;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import moe.caa.multilogin.api.auth.AuthResult;
import moe.caa.multilogin.api.auth.Property;
import moe.caa.multilogin.bukkit.injector.BukkitInjector;
import moe.caa.multilogin.bukkit.injector.Contents;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

/**
 * JDK 代理 MinecraftSessionService
 */
public class MinecraftSessionServiceInvocationHandler implements InvocationHandler {
    private final BukkitInjector injector;
    private final MinecraftSessionService origin;

    public MinecraftSessionServiceInvocationHandler(BukkitInjector injector, MinecraftSessionService origin) {
        this.injector = injector;
        this.origin = origin;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean matched = false;
        if (method.getReturnType().equals(GameProfile.class)) {
            if (method.getParameterCount() == 2 || method.getParameterCount() == 3) {
                if (method.getParameterTypes()[0].equals(GameProfile.class)) {
                    if (method.getParameterTypes()[1].equals(String.class)) {
                        if (method.getParameterCount() == 3) {
                            if (method.getParameterTypes()[2].equals(InetAddress.class)) {
                                matched = true;
                            }
                        } else {
                            matched = true;
                        }
                    }
                }
            }
        }
        if (!matched) {
            return method.invoke(origin, args);
        }
        GameProfile profile = ((GameProfile) args[0]);
        String serverId = (String) args[1];
        String ip = "";
        if (args.length == 3) {
            if (args[2] != null) {
                ip = getIp(profile.getName(), (SocketAddress) args[2]);
            } else {
                ip = getIp(profile.getName(), null);
            }
        } else {
            ip = getIp(profile.getName(), null);
        }
        AuthResult authResult = injector.getApi().getAuthHandler().auth(profile.getName(), serverId, ip);
        if (authResult.isAllowed()) {
            return generate(authResult.getResponse());
        } else {
            Contents.getKickMessageEntryMap().put(profile.getName(), Contents.KickMessageEntry.of(authResult.getKickMessage()));
            return null;
        }
    }

    private String getIp(String name, SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getAddress().getHostName();
        }
        SocketAddress socketAddress = injector.getLoginStateSocketAddressGetter().get(name);
        if (socketAddress instanceof InetSocketAddress) {
            return ((InetSocketAddress) socketAddress).getAddress().getHostName();
        }
        return "";
    }

    private GameProfile generate(moe.caa.multilogin.api.auth.GameProfile response) {
        GameProfile result = new GameProfile(response.getId(), response.getName());
        if (response.getPropertyMap() != null) {
            for (Map.Entry<String, Property> entry : response.getPropertyMap().entrySet()) {
                result.getProperties().put(entry.getKey(),
                        new com.mojang.authlib.properties.Property(entry.getValue().getName(), entry.getValue().getValue(), entry.getValue().getSignature()));
            }
        }
        return result;
    }
}