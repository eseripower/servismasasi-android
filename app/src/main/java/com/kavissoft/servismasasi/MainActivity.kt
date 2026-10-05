package com.kavissoft.servismasasi

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.kavissoft.servismasasi.ui.Uygulama

class MainActivity : FragmentActivity() {
    private val durum: Durum by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Ekran görüntüsü, ekran kaydı ve "son uygulamalar" önizlemesi kapalı
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent { Uygulama(durum, this) }
    }

    override fun onStart() {
        super.onStart()
        durum.onPlan()
    }

    override fun onStop() {
        super.onStop()
        durum.arkaPlan()
    }
}
