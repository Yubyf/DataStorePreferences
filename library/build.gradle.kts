import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}
android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        targetSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    api("androidx.datastore:datastore-preferences:1.0.0")

    testImplementation("junit:junit:4.13.2")
    //For runBlockingTest, CoroutineDispatcher etc.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}

//region Publish
val snapshot: String by project
val buildType = "release"
val mavenGroupId: String by project
val mavenArtifactId: String by project
val mavenVersion: String by project
val pomName: String by project
val pomDescription: String by project
val pomUrl: String by project
val pomLicenseName: String by project
val pomLicenseUrl: String by project
val developerName: String by project
val developerEmail: String by project

// Maven server credentials in local.properties
val localProperties = gradleLocalProperties(rootDir)

val sourcesJar by tasks.register("sourcesJar", Jar::class) {
    from(android.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = uri(
                "https://s01.oss.sonatype.org/"
                        + if (snapshot.toBoolean()) "content/repositories/snapshots/" else "service/local/staging/deploy/maven2/"
            )
            credentials {
                username = localProperties["OSSRH_USERNAME"] as String?
                password = localProperties["OSSRH_PASSWORD"] as String?
            }
        }
    }

    publications {
        afterEvaluate {
            create<MavenPublication>(buildType) {
                from(components[buildType])

                artifact(sourcesJar)

                groupId = mavenGroupId
                artifactId = mavenArtifactId
                version = "$mavenVersion${if (snapshot.toBoolean()) "-SNAPSHOT" else ""}"

                pom {
                    packaging = "aar"
                    name.set(pomName)
                    description.set(pomDescription)
                    url.set(pomUrl)
                    licenses {
                        license {
                            name.set(pomLicenseName)
                            url.set(pomLicenseUrl)
                        }
                    }
                    developers {
                        developer {
                            name.set(developerName)
                            email.set(developerEmail)
                        }
                    }
                    scm {
                        url.set(pom.url.get())
                        connection.set("scm:git:${url.get()}.git")
                        developerConnection.set("scm:git:${url.get()}.git")
                    }
                }
            }
        }
    }
}

signing {
    // Load gpg info in gradle.properties(global)
    useGpgCmd()
    afterEvaluate {
        sign(publishing.publications[buildType])
    }
}
//endregion