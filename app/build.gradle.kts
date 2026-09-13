plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.freevrsbs"; compileSdk = 35
    defaultConfig { applicationId = "com.freevrsbs"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
}

dependencies { implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.activity:activity-ktx:1.10.0"); implementation("androidx.datastore:datastore-preferences:1.1.2") }
