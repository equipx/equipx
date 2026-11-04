package com.equipx.app.domain

enum class Role { STUDENT, TEACHER }

data class User(val email: String, val name: String, val role: Role)
