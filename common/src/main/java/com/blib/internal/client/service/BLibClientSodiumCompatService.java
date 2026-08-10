package com.blib.internal.client.service;

import org.jetbrains.annotations.ApiStatus;

/**
 * Loader-agnostic check for "is Sodium (or an embedded fork of it) currently loaded?" Result is consulted lazily by
 * {@link com.blib.internal.client.posteffect.BLibSodiumCompat} and cached after first call.
 */
@ApiStatus.Internal
public interface BLibClientSodiumCompatService {

    boolean isSodiumActive();
}
