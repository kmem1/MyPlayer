package com.kmem.myplayer.core_utils.ui.base

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kmem.myplayer.core_utils.SResult

interface ISResultContainer {
    val resultLiveData: LiveData<*>?
    val supportLiveData: MutableLiveData<SResult<*>>?
    val toastLiveData: MutableLiveData<SResult.Toast>
    val navLiveData: MutableLiveData<SResult.NavResult>
}