/*
 * MIT License
 *
 * Copyright (c) 2017 Tuomo Heino
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package xyz.hetula.homefy.service.protocol

class VersionInfo {
    val name: String
    val version: String
    val authentication: AuthType
    var databaseSize = 0
        set(value) {databaseSize = value}

    constructor() {
        this.name = "DEFAULT"
        this.version = "0"
        this.authentication = AuthType.NONE
    }

    constructor(name: String, version: String, authentication: AuthType) {
        this.name = name
        this.version = version
        this.authentication = authentication
    }

    override fun toString(): String {
        return "VersionInfo{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", authentication=" + authentication +
                '}'
    }

    enum class AuthType {
        NONE,
        BASIC,
        OAUTH2
    }
}