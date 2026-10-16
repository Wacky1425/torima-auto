package com.neru.powlautotest

import android.content.Context

/**
 * Explicit one-release experiment requested by the user.
 * The normal post-ad 4-second quiet period and additional stable-screen
 * confirmation are skipped. The setting does NOT bypass target selection,
 * explicit gacha UI, forbidden share/skip-ticket actions or current-window
 * checks. The UI exposes a one-tap way to restore guarded mode.
 */
internal object FastAdMode {
 private const val PREF="torima_experimental_fast_ad"
 private const val KEY="skip_post_ad_guard"
 fun enabled(ctx:Context):Boolean=
  ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(KEY,true)
 fun setEnabled(ctx:Context,value:Boolean){
  ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
   .putBoolean(KEY,value).apply()
 }
 fun shouldApplyPostAdGuard(fast:Boolean)=!fast
 fun shouldStartPostAdGuard(fast:Boolean,adClose:Boolean)=!fast && adClose
}
