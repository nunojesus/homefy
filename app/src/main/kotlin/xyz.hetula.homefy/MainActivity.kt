/*
 * Copyright (c) 2018 Tuomo Heino
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.hetula.homefy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import xyz.hetula.homefy.library.LibraryFragment
import xyz.hetula.homefy.service.HomefyService
import xyz.hetula.homefy.setup.SetupFragment

/**
 * @author Tuomo Heino
 * @version 1.0
 * @since 1.0
 */
class MainActivity : HomefyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val startService = Intent(applicationContext, HomefyService::class.java)
        startService(startService)
    }

    override fun serviceConnected(service: HomefyService) {
        super.serviceConnected(service)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.container,
                        if (service.getLibrary().isLibraryReady()) {
                            LibraryFragment()
                        } else {
                            SetupFragment()
                        })
                .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_shutdown -> {
                doShutdown()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun doShutdown() {
        Log.d("MainActivity", "Shutting down!")
        stopService(Intent(applicationContext, HomefyService::class.java))
        finishAffinity()
    }
}
