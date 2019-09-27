package com.microsoft.tfs

import com.microsoft.tfs.core.TFSTeamProjectCollection
import com.microsoft.tfs.core.clients.versioncontrol.VersionControlClient
import com.microsoft.tfs.core.clients.versioncontrol.Workstation
import com.microsoft.tfs.core.clients.versioncontrol.path.LocalPath
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.PendingSet
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.RecursionType
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.Workspace
import com.microsoft.tfs.core.clients.versioncontrol.specs.ItemSpec
import com.microsoft.tfs.core.config.persistence.DefaultPersistenceStoreProvider
import com.microsoft.tfs.core.httpclient.Credentials
import java.nio.file.Path

class TfsClient(path: Path, credentials: Credentials) {
    val client: VersionControlClient
    val workspace: Workspace
    init{
        val workstation = Workstation.getCurrent(DefaultPersistenceStoreProvider.INSTANCE)
        val workspaceInfo = workstation.getLocalWorkspaceInfo(path.toString())
        val serverUri = workspaceInfo.serverURI

        val collection = TFSTeamProjectCollection(serverUri, credentials)
        client = collection.versionControlClient
        workspace = client.getLocalWorkspace(path.toString(), true)
    }

    val workspaceName
        get() = workspace.name

    val workspaceOwner
        get() = workspace.ownerName

    fun status(paths: List<Path>): Array<PendingSet> {
        val itemSpecs = ItemSpec.fromStrings(paths.map { LocalPath.canonicalize(it.toString()) }.toTypedArray(), RecursionType.FULL)
        workspace.workspaceWatcher.forceFullScan() // TODO: Remove!
        return client.queryPendingSets(itemSpecs, false, workspaceName, workspaceOwner, true)
    }
}