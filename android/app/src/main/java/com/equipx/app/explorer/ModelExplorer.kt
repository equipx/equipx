package com.equipx.app.explorer

/** Commands the app can send to a 3D viewer. Implemented by the WebView viewer; SceneView could replace it. */
interface ModelExplorer {
    fun loadModel(glbUrl: String, hotspotsUrl: String?, cameraOrbit: String?)
    fun selectPart(partId: String?)
    fun focusPart(partId: String)
    fun resetCamera()
}

/** Events a 3D viewer reports back. Always invoked on the main thread. */
interface ModelExplorerListener {
    fun onHotspotTap(partId: String)
    fun onModelLoaded()
    fun onModelError(message: String)
}
