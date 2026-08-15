package com.ultrabytecoder.kryptakeep.data

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * Key/value storage in `Library/Application Support/KryptaKeep/settings.plist`,
 * marked with NSURLIsExcludedFromBackupKey so the wrapped DEK, PIN salt and lockout
 * state never leave the device via iTunes/Finder or iCloud device backups
 * (B3/B4: NSUserDefaults' plaintext plist under Library/Preferences would be backed up).
 */
actual class SettingsStorage actual constructor(context: Any?) {

    private val directoryUrl: NSURL = createExcludedDirectory()
    private val fileUrl: NSURL = directoryUrl.URLByAppendingPathComponent("settings.plist")!!

    private fun createExcludedDirectory(): NSURL {
        val appSupportPath =
            NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
                .first() as String
        val dir = NSURL.fileURLWithPath(appSupportPath)
            .URLByAppendingPathComponent("KryptaKeep", isDirectory = true)!!
        NSFileManager.defaultManager.createDirectoryAtURL(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        dir.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        return dir
    }

    private fun loadDict(): NSMutableDictionary =
        NSMutableDictionary.dictionaryWithContentsOfFile(fileUrl.path!!) ?: NSMutableDictionary.dictionary()

    actual fun putString(key: String, value: String) {
        val dict = loadDict()
        dict.setObject(value, forKey = key)
        dict.writeToFile(fileUrl.path!!, atomically = true)
    }

    actual fun getString(key: String): String? =
        loadDict().objectForKey(key) as? String

    actual fun remove(key: String) {
        val dict = loadDict()
        dict.removeObjectForKey(key)
        dict.writeToFile(fileUrl.path!!, atomically = true)
    }
}