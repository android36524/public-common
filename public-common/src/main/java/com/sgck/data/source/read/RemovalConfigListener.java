package com.sgck.data.source.read;

import com.google.common.cache.RemovalNotification;

@SuppressWarnings("hiding")
public interface RemovalConfigListener<String, ReadConfig> {
	/**
	 * Notifies the listener that a removal occurred at some point in the past.
	 *
	 * <p>
	 * This does not always signify that the key is now absent from the cache,
	 * as it may have already been re-added.
	 */
	// Technically should accept RemovalNotification<? extends K, ? extends V>,
	// but because
	// RemovalNotification is guaranteed covariant, let's make users' lives
	// simpler.
	void onRemoval(RemovalNotification<String, ReadConfig> removalNotification);
}