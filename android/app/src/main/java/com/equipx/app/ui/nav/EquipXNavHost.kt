package com.equipx.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.equipx.app.ui.credits.CreditsScreen
import com.equipx.app.ui.device.DeviceDetailScreen
import com.equipx.app.ui.explorer.ExplorerScreen
import com.equipx.app.ui.login.LoginScreen
import com.equipx.app.ui.main.MainScreen
import com.equipx.app.ui.part.PartDetailScreen
import com.equipx.app.ui.procedure.ProcedureScreen
import com.equipx.app.ui.quiz.QuizScreen
import com.equipx.app.ui.troubleshoot.TroubleshootScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val DEVICE = "device/{deviceId}"
    const val EXPLORER = "explorer/{deviceId}?partId={partId}"
    const val PART = "part/{deviceId}/{partId}"
    const val PROCEDURE = "procedure/{deviceId}/{procedureId}"
    const val QUIZ = "quiz/{deviceId}/{quizId}"
    const val FAULT = "fault/{deviceId}/{faultId}"
    const val CREDITS = "credits"

    fun device(deviceId: String) = "device/$deviceId"
    fun explorer(deviceId: String, partId: String? = null) =
        if (partId == null) "explorer/$deviceId" else "explorer/$deviceId?partId=$partId"
    fun part(deviceId: String, partId: String) = "part/$deviceId/$partId"
    fun procedure(deviceId: String, procedureId: String) = "procedure/$deviceId/$procedureId"
    fun quiz(deviceId: String, quizId: String) = "quiz/$deviceId/$quizId"
    fun fault(deviceId: String, faultId: String) = "fault/$deviceId/$faultId"
}

private fun strArg(name: String) = navArgument(name) { type = NavType.StringType }

/** Runs [block] only while this back-stack entry is RESUMED; drops repeat taps during transitions. */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

@Composable
fun EquipXNavHost(startRoute: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.LOGIN) { entry ->
            LoginScreen(onLoggedIn = { entry.ifResumed { nav.navigate(Routes.MAIN) { popUpTo(Routes.LOGIN) { inclusive = true } } } })
        }
        composable(Routes.MAIN) { entry ->
            MainScreen(
                onOpenExplorer = { entry.ifResumed { nav.navigate(Routes.explorer(it)) } },
                onOpenDevice = { entry.ifResumed { nav.navigate(Routes.device(it)) } },
                onOpenProcedure = { d, p -> entry.ifResumed { nav.navigate(Routes.procedure(d, p)) } },
                onOpenQuiz = { d, q -> entry.ifResumed { nav.navigate(Routes.quiz(d, q)) } },
                onOpenCredits = { entry.ifResumed { nav.navigate(Routes.CREDITS) } },
                onLoggedOut = { entry.ifResumed { nav.navigate(Routes.LOGIN) { popUpTo(Routes.MAIN) { inclusive = true } } } },
            )
        }
        composable(Routes.DEVICE, arguments = listOf(strArg("deviceId"))) { entry ->
            val deviceId = entry.arguments?.getString("deviceId").orEmpty()
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = { entry.ifResumed { nav.popBackStack() } },
                onExplore = { entry.ifResumed { nav.navigate(Routes.explorer(deviceId)) } },
                onOpenPart = { entry.ifResumed { nav.navigate(Routes.part(deviceId, it)) } },
                onOpenProcedure = { entry.ifResumed { nav.navigate(Routes.procedure(deviceId, it)) } },
                onOpenQuiz = { entry.ifResumed { nav.navigate(Routes.quiz(deviceId, it)) } },
                onOpenFault = { entry.ifResumed { nav.navigate(Routes.fault(deviceId, it)) } },
            )
        }
        composable(
            Routes.EXPLORER,
            arguments = listOf(strArg("deviceId"), navArgument("partId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val deviceId = entry.arguments?.getString("deviceId").orEmpty()
            ExplorerScreen(
                deviceId = deviceId,
                initialPartId = entry.arguments?.getString("partId"),
                onBack = { entry.ifResumed { nav.popBackStack() } },
                onOpenPart = { d, p -> entry.ifResumed { nav.navigate(Routes.part(d, p)) } },
            )
        }
        composable(Routes.PART, arguments = listOf(strArg("deviceId"), strArg("partId"))) { entry ->
            val deviceId = entry.arguments?.getString("deviceId").orEmpty()
            val partId = entry.arguments?.getString("partId").orEmpty()
            PartDetailScreen(
                deviceId = deviceId,
                partId = partId,
                onBack = { entry.ifResumed { nav.popBackStack() } },
                onShowIn3D = { entry.ifResumed { showInExplorer(nav, deviceId, partId) } },
            )
        }
        composable(Routes.PROCEDURE, arguments = listOf(strArg("deviceId"), strArg("procedureId"))) { entry ->
            val deviceId = entry.arguments?.getString("deviceId").orEmpty()
            ProcedureScreen(
                deviceId = deviceId,
                procedureId = entry.arguments?.getString("procedureId").orEmpty(),
                onBack = { entry.ifResumed { nav.popBackStack() } },
                onOpenPart = { entry.ifResumed { nav.navigate(Routes.part(deviceId, it)) } },
            )
        }
        composable(Routes.QUIZ, arguments = listOf(strArg("deviceId"), strArg("quizId"))) { entry ->
            QuizScreen(
                deviceId = entry.arguments?.getString("deviceId").orEmpty(),
                quizId = entry.arguments?.getString("quizId").orEmpty(),
                onBack = { entry.ifResumed { nav.popBackStack() } },
            )
        }
        composable(Routes.FAULT, arguments = listOf(strArg("deviceId"), strArg("faultId"))) { entry ->
            val deviceId = entry.arguments?.getString("deviceId").orEmpty()
            TroubleshootScreen(
                deviceId = deviceId,
                faultId = entry.arguments?.getString("faultId").orEmpty(),
                onBack = { entry.ifResumed { nav.popBackStack() } },
                onOpenPart = { entry.ifResumed { nav.navigate(Routes.part(deviceId, it)) } },
            )
        }
        composable(Routes.CREDITS) { entry -> CreditsScreen(onBack = { entry.ifResumed { nav.popBackStack() } }) }
    }
}

/** From part detail: reuse the explorer underneath when we came from it, otherwise open it focused on the part. */
private fun showInExplorer(nav: NavHostController, deviceId: String, partId: String) {
    val previous = nav.previousBackStackEntry?.destination?.route
    val cameFromSameExplorer = previous == Routes.EXPLORER &&
        nav.previousBackStackEntry?.arguments?.getString("deviceId") == deviceId
    if (cameFromSameExplorer) nav.popBackStack() else nav.navigate(Routes.explorer(deviceId, partId))
}
