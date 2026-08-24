package dev.ryanhcode.sable.platform;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface SableLoaderPlatform {
	SableLoaderPlatform INSTANCE = SablePlatformUtil.load(SableLoaderPlatform.class);
	
	boolean isModLoaded(String modId);

	String getModVersion(String modId);
}
