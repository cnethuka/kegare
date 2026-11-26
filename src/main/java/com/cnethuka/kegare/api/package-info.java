/**
 * Kegare's addon API: the only part of Kegare that addons should use. Everything outside {@code com.cnethuka.kegare.api}
 * is internal and may change in any release.
 * <p>
 * <b>Experimental until Kegare 1.0.</b> Within one minor version (0.4.0, 0.4.1, ...) nothing here changes in a way that
 * breaks an addon; a new minor version (0.5.0) may. Declare your Kegare dependency as one minor range, counting its pre-releases:
 * {@code versionRange = "[0.4.0-alpha,0.5.0-alpha)"} (version ranges compare like Maven's, where 0.4.0-alpha comes before 0.4, so
 * {@code "[0.4,0.5)"} would refuse 0.4.0-alpha and let 0.5.0-alpha in).
 * <p>
 * Start at {@link com.cnethuka.kegare.api.KegareApi}. The guide is {@code docs/addon-api.md} in Kegare's repository.
 */
@ApiStatus.Experimental
package com.cnethuka.kegare.api;

import org.jetbrains.annotations.ApiStatus;
