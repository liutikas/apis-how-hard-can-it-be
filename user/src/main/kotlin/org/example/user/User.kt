package org.example.user

import org.example.Example4

class User private constructor() {
    fun makeCall() {
        Example4().myMethod("Aurimas")
    }
}
