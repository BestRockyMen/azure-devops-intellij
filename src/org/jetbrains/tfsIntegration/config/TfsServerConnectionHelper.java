package org.jetbrains.tfsIntegration.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.microsoft.schemas.teamfoundation._2005._06.services.registration._03.*;
import com.microsoft.schemas.teamfoundation._2005._06.services.serverstatus._03.CheckAuthentication;
import com.microsoft.schemas.teamfoundation._2005._06.services.serverstatus._03.CheckAuthenticationResponse;
import com.microsoft.schemas.teamfoundation._2005._06.services.serverstatus._03.ServerStatusStub;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.QueryWorkspaces;
import com.microsoft.schemas.teamfoundation._2005._06.versioncontrol.clientservices._03.Workspace;
import com.microsoft.webservices.*;
import com.microsoft.wsdl.types.Guid;
import org.apache.axis2.context.ConfigurationContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.tfsIntegration.core.TFSBundle;
import org.jetbrains.tfsIntegration.core.TFSConstants;
import org.jetbrains.tfsIntegration.core.TfsBeansHolder;
import org.jetbrains.tfsIntegration.core.configuration.Credentials;
import org.jetbrains.tfsIntegration.core.configuration.TFSConfigurationManager;
import org.jetbrains.tfsIntegration.core.tfs.TfsUtil;
import org.jetbrains.tfsIntegration.core.tfs.Workstation;
import org.jetbrains.tfsIntegration.exceptions.HostNotApplicableException;
import org.jetbrains.tfsIntegration.exceptions.TfsException;
import org.jetbrains.tfsIntegration.exceptions.UserCancelledException;
import org.jetbrains.tfsIntegration.ui.ChooseTeamProjectCollectionDialog;
import org.jetbrains.tfsIntegration.webservice.TfsRequestManager;
import org.jetbrains.tfsIntegration.webservice.WebServiceHelper;

import javax.swing.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TfsServerConnectionHelper {

  private static final Logger LOG = Logger.getInstance(TfsServerConnectionHelper.class.getName());

  public static class ServerDescriptor {
    public final Credentials authorizedCredentials;
    @Nullable public final TfsBeansHolder beans;
    //public final String icc

    protected ServerDescriptor(Credentials authorizedCredentials, TfsBeansHolder beans) {
      this.authorizedCredentials = authorizedCredentials;
      this.beans = beans;
    }

    public String getUserName() {
      return authorizedCredentials.getUserName();
    }
  }

  private static class Tfs200xServerDescriptor extends ServerDescriptor {
    public final String instanceId;
    public final Workspace[] workspaces;

    public Tfs200xServerDescriptor(String instanceId,
                                   Credentials authorizedCredentials,
                                   Workspace[] workspaces,
                                   TfsBeansHolder servicesPaths) {
      super(authorizedCredentials, servicesPaths);
      this.instanceId = instanceId;
      this.workspaces = workspaces;
    }
  }

  private static class Tfs2010ServerDescriptor extends ServerDescriptor {
    public final Collection<TeamProjectCollectionDescriptor> teamProjectCollections;

    public Tfs2010ServerDescriptor(Collection<TeamProjectCollectionDescriptor> teamProjectCollections,
                                   Credentials authorizedCredentials, TfsBeansHolder servicesPaths) {
      super(authorizedCredentials, servicesPaths);
      this.teamProjectCollections = teamProjectCollections;
    }
  }


  public static class TeamProjectCollectionDescriptor {
    public final String name;
    public final String instanceId;

    public TeamProjectCollectionDescriptor(String name, String instanceId) {
      this.name = name;
      this.instanceId = instanceId;
    }
  }

  public static void ensureAuthenticated(Object projectOrComponent, URI serverUri) throws TfsException {
    TfsRequestManager.getInstance(serverUri)
      .executeRequestInForeground(projectOrComponent, true, null, true, new TfsRequestManager.Request<Void>(null) {
        @Override
        public Void execute(Credentials credentials, URI serverUri, ProgressIndicator pi) throws Exception {
          connect(serverUri, credentials, true, pi);
          return null;
        }

        @NotNull
        @Override
        public String getProgressTitle(Credentials credentials, URI serverUri) {
          return TFSBundle.message("connect.to", TfsUtil.getPresentableUri(serverUri));
        }
      });
  }

  public static class AddServerResult {
    public final URI uri;
    public final String instanceId;
    public final Credentials authorizedCredentials;
    public final Workspace[] workspaces;
    public final String workspacesLoadError;
    @Nullable public final TfsBeansHolder beans;

    public AddServerResult(URI uri,
                           String instanceId,
                           Credentials authorizedCredentials,
                           Workspace[] workspaces,
                           String workspacesLoadError, TfsBeansHolder beans) {
      this.uri = uri;
      this.instanceId = instanceId;
      this.authorizedCredentials = authorizedCredentials;
      this.workspaces = workspaces;
      this.workspacesLoadError = workspacesLoadError;
      this.beans = beans;
    }
  }

  @Nullable
  public static AddServerResult addServer(final JComponent parentComponent) {
    TfsRequestManager.Request<Trinity<URI, ServerDescriptor, Credentials>> connectRequest =
      new TfsRequestManager.Request<Trinity<URI, ServerDescriptor, Credentials>>(null) {
        @Override
        public Trinity<URI, ServerDescriptor, Credentials> execute(Credentials credentials,
                                                                   URI serverUri,
                                                                   @Nullable ProgressIndicator pi)
          throws Exception {
          ServerDescriptor serverDescriptor = connect(serverUri, credentials, false, pi);
          // check duplicate instance id for TFS 2005/2008, TFS 2010 will be checked later when project collection is selected
          if (serverDescriptor instanceof Tfs200xServerDescriptor &&
              TFSConfigurationManager.getInstance()
                .serverKnown(((Tfs200xServerDescriptor)serverDescriptor).instanceId)) {
            throw new TfsException(TFSBundle.message("duplicate.server"));
          }

          if (!credentials.getUserName().equalsIgnoreCase(serverDescriptor.getUserName())) {
            LOG.warn("authorized user mismatch: current=" + credentials.getQualifiedUsername() +
                     ", authorized: " + serverDescriptor.authorizedCredentials);
            throw new TfsException(TFSBundle.message("authorized.user.mismatch"));
          }

          if (serverDescriptor instanceof Tfs2010ServerDescriptor) {
            Collection<TeamProjectCollectionDescriptor> teamProjectCollections =
              ((Tfs2010ServerDescriptor)serverDescriptor).teamProjectCollections;
            if (teamProjectCollections.size() == 0) {
              throw new TfsException(TFSBundle.message("no.team.project.collections"));
            }

            boolean newCollection = false;
            for (TeamProjectCollectionDescriptor teamProjectCollection : teamProjectCollections) {
              if (!TFSConfigurationManager.getInstance().serverKnown(teamProjectCollection.instanceId)) {
                newCollection = true;
                break;
              }
            }
            if (!newCollection) {
              throw new TfsException(TFSBundle.message("all.team.project.collections.duplicate"));
            }
          }
          return Trinity.create(serverUri, serverDescriptor, credentials);
        }

        @NotNull
        @Override
        public String getProgressTitle(Credentials credentials, URI serverUri) {
          return TFSBundle.message("connect.to", TfsUtil.getPresentableUri(serverUri));
        }

        @Override
        public boolean retrieveAuthorizedCredentials() {
          return false;
        }
      };

    Trinity<URI, ServerDescriptor, Credentials> result;
    try {
      result = TfsRequestManager.getInstance(null).executeRequestInForeground(parentComponent, true, null, true, connectRequest);
    }
    catch (UserCancelledException e) {
      return null;
    }
    catch (TfsException e) {
      // should not float up through the dialog
      LOG.error(e);
      return null;
    }

    final ServerDescriptor serverDescriptor = result.second;

    if (serverDescriptor instanceof Tfs200xServerDescriptor) {
      Tfs200xServerDescriptor tfs200xServerDescriptor = (Tfs200xServerDescriptor)serverDescriptor;
      return new AddServerResult(result.first, tfs200xServerDescriptor.instanceId, serverDescriptor.authorizedCredentials,
                                 tfs200xServerDescriptor.workspaces, null, serverDescriptor.beans);
    }
    else {
      Collection<TeamProjectCollectionDescriptor> teamProjectCollections =
        ((Tfs2010ServerDescriptor)serverDescriptor).teamProjectCollections;
      TeamProjectCollectionDescriptor collection;
      if (teamProjectCollections.size() == 1) {
        collection = teamProjectCollections.iterator().next();
      }
      else {
        ChooseTeamProjectCollectionDialog d2 =
          new ChooseTeamProjectCollectionDialog(parentComponent, result.first.toString(), teamProjectCollections);
        d2.show();
        if (!d2.isOK()) {
          return null;
        }
        collection = d2.getSelectedItem();
        LOG.assertTrue(collection != null);
      }

      URI collectionUri = getCollectionUri(result.first, collection);
      final TfsBeansHolder beans = new TfsBeansHolder(collectionUri);
      TfsRequestManager.Request<Workspace[]> loadWorkspacesRequest = new TfsRequestManager.Request<Workspace[]>(null) {
        @Override
        public Workspace[] execute(Credentials credentials, URI serverUri, @Nullable ProgressIndicator pi) throws Exception {
          return queryWorkspaces(serverDescriptor.authorizedCredentials, pi, beans);
        }

        @NotNull
        @Override
        public String getProgressTitle(Credentials credentials, URI serverUri) {
          return TFSBundle.message("connect.to", TfsUtil.getPresentableUri(serverUri));
        }

        @Override
        public boolean retrieveAuthorizedCredentials() {
          return false;
        }
      };

      Workspace[] workspaces = null;
      String workspacesLoadError = null;
      try {
        workspaces = TfsRequestManager.getInstance(collectionUri)
          .executeRequestInForeground(parentComponent, false, result.third, true, loadWorkspacesRequest);
      }
      catch (UserCancelledException e) {
        return null;
      }
      catch (TfsException e) {
        workspacesLoadError = e.getMessage();
      }
      return new AddServerResult(collectionUri, collection.instanceId, serverDescriptor.authorizedCredentials, workspaces,
                                 workspacesLoadError, serverDescriptor.beans);
    }
  }

  private static URI getCollectionUri(URI serverUri, TeamProjectCollectionDescriptor collection) {
    String path = serverUri.getPath();
    if (!path.endsWith("/")) {
      path += "/";
    }
    try {
      return new URI(serverUri.getScheme(), serverUri.getUserInfo(), serverUri.getHost(), serverUri.getPort(), path + collection.name, null,
                     null);
    }
    catch (URISyntaxException e) {
      LOG.error(e);
      return null;
    }
  }

  public static ServerDescriptor connect(URI uri, Credentials credentials, boolean justAuthenticate, @Nullable ProgressIndicator pi)
    throws RemoteException, TfsException {
    if (pi != null) {
      pi.setText(TFSBundle.message("connecting.to.server"));
    }

    if (justAuthenticate) {
      uri = getBareUri(uri);
    }
    ConfigurationContext context = WebServiceHelper.getStubConfigurationContext();

    RegistrationStub registrationStub = new RegistrationStub(context, TfsUtil.appendPath(uri, TFSConstants.REGISTRATION_ASMX));
    WebServiceHelper.setupStub(registrationStub, credentials, uri);
    final GetRegistrationEntries getRegistrationEntriesParam = new GetRegistrationEntries();
    getRegistrationEntriesParam.setToolId(TFSConstants.TOOL_ID_FRAMEWORK);
    GetRegistrationEntriesResponse registrationEntries = registrationStub.getRegistrationEntries(getRegistrationEntriesParam);

    FrameworkRegistrationEntry[] arrayOfEntries = registrationEntries.getGetRegistrationEntriesResult().getRegistrationEntry();
    if (arrayOfEntries != null && arrayOfEntries.length > 0) {
      // TFS 2010 -> get team project collections
      LocationWebServiceStub locationService =
        new LocationWebServiceStub(context, TfsUtil.appendPath(uri, TFSConstants.LOCATION_SERVICE_ASMX));
      WebServiceHelper.setupStub(locationService, credentials, uri);
      Connect connectParam = new Connect();
      connectParam.setConnectOptions(TFSConstants.INCLUDE_SERVICES_CONNECTION_OPTION);
      ArrayOfServiceTypeFilter serviceTypeFilters = new ArrayOfServiceTypeFilter();
      ServiceTypeFilter filter = new ServiceTypeFilter();
      filter.setServiceType("*");
      filter.setIdentifier(TFSConstants.FRAMEWORK_SERVER_DATA_PROVIDER_FILTER_GUID);
      serviceTypeFilters.addServiceTypeFilter(filter);
      connectParam.setServiceTypeFilters(serviceTypeFilters);
      connectParam.setLastChangeId(-1);
      ConnectResponse connectResponse = locationService.connect(connectParam);

      ArrayOfKeyValueOfStringString userProps = connectResponse.getConnectResult().getAuthorizedUser().getAttributes();
      String domain = getPropertyValue(userProps, TFSConstants.DOMAIN);
      String userName = getPropertyValue(userProps, TFSConstants.ACCOUNT);
      if (justAuthenticate) {
        return new ServerDescriptor(new Credentials(userName, domain, credentials.getPassword(), credentials.isStorePassword()),
                                    null);
      }

      if (pi != null) {
        pi.setText(TFSBundle.message("loading.team.project.collections"));
      }

      ServiceDefinition[] serviceDefinitions =
        connectResponse.getConnectResult().getLocationServiceData().getServiceDefinitions().getServiceDefinition();
      if (serviceDefinitions == null) {
        LOG.warn("service definitions node is null");
        throw new HostNotApplicableException(null);
      }

      String catalogServicePath = null;
      for (ServiceDefinition serviceDefinition : serviceDefinitions) {
        if (TFSConstants.CATALOG_SERVICE_CONFIG_GUID.equalsIgnoreCase(serviceDefinition.getIdentifier().getGuid())) {
          catalogServicePath = serviceDefinition.getRelativePath();
        }
      }
      if (catalogServicePath == null) {
        LOG.warn("catalog service not found by giud");
        throw new HostNotApplicableException(null);
      }

      Guid catalogResourceId = connectResponse.getConnectResult().getCatalogResourceId();
      CatalogWebServiceStub catalogService = new CatalogWebServiceStub(context, TfsUtil.appendPath(uri, catalogServicePath));
      WebServiceHelper.setupStub(catalogService, credentials, uri);
      QueryResources queryResourcesParam = new QueryResources();
      ArrayOfGuid resourceIdentitiers = new ArrayOfGuid();
      resourceIdentitiers.addGuid(catalogResourceId);
      queryResourcesParam.setResourceIdentifiers(resourceIdentitiers);
      QueryResourcesResponse queryResourcesResponse = catalogService.queryResources(queryResourcesParam);
      String referencePath = null;
      for (CatalogResource catalogResource : queryResourcesResponse.getQueryResourcesResult().getCatalogResources().getCatalogResource()) {
        if (catalogResource.getIdentifier().getGuid().equals(catalogResourceId.getGuid())) {
          referencePath = catalogResource.getNodeReferencePaths().getString()[0];
          break;
        }
      }
      if (referencePath == null) {
        throw new HostNotApplicableException(null);
      }

      QueryNodes queryNodesParam = new QueryNodes();
      ArrayOfString pathSpecs = new ArrayOfString();
      pathSpecs.addString(referencePath + TFSConstants.SINGLE_RECURSE_STAR);
      queryNodesParam.setPathSpecs(pathSpecs);
      ArrayOfGuid resourceTypeFilters = new ArrayOfGuid();
      resourceTypeFilters.addGuid(TFSConstants.PROJECT_COLLECTION_GUID);
      queryNodesParam.setResourceTypeFilters(resourceTypeFilters);
      QueryNodesResponse queryNodesResponse = catalogService.queryNodes(queryNodesParam);
      CatalogResource[] teamProjectCollections = queryNodesResponse.getQueryNodesResult().getCatalogResources().getCatalogResource();

      List<TeamProjectCollectionDescriptor> descriptors = new ArrayList<TeamProjectCollectionDescriptor>(teamProjectCollections.length);
      for (CatalogResource collectionNode : teamProjectCollections) {
        String instanceId = getPropertyValue(collectionNode.getProperties(), TFSConstants.INSTANCE_ID_ATTRIBUTE);
        if (instanceId == null) {
          throw new HostNotApplicableException(null);
        }
        descriptors.add(new TeamProjectCollectionDescriptor(collectionNode.getDisplayName(), instanceId));
      }

      TfsBeansHolder beans = new TfsBeansHolder(uri);
      return new Tfs2010ServerDescriptor(descriptors,
                                         new Credentials(userName, domain, credentials.getPassword(), credentials.isStorePassword()),
                                         beans);
    }
    else {
      if (justAuthenticate) {
        String authorizedCredentials = getAuthorizedCredentialsFor200x(context, uri, credentials);
        return new ServerDescriptor(new Credentials(authorizedCredentials, credentials.getPassword(), credentials.isStorePassword()),
                                    null);
      }

      getRegistrationEntriesParam.setToolId(TFSConstants.TOOL_ID_TFS);
      registrationEntries = registrationStub.getRegistrationEntries(getRegistrationEntriesParam);
      arrayOfEntries = registrationEntries.getGetRegistrationEntriesResult().getRegistrationEntry();

      String instanceId = null;
      if (arrayOfEntries != null) {
        outer_loop:
        for (FrameworkRegistrationEntry entry : arrayOfEntries) {
          if (TFSConstants.TOOL_ID_TFS.equals(entry.getType())) {
            for (RegistrationExtendedAttribute2 attribute : entry.getRegistrationExtendedAttributes().getRegistrationExtendedAttribute()) {
              if (TFSConstants.INSTANCE_ID_ATTRIBUTE.equals(attribute.getName())) {
                instanceId = attribute.getValue();
                break outer_loop;
              }
            }
          }
        }
      }
      if (instanceId == null) {
        throw new HostNotApplicableException(null);
      }

      String qName = getAuthorizedCredentialsFor200x(context, uri, credentials);
      Credentials authorizedCredentials = new Credentials(qName, credentials.getPassword(), credentials.isStorePassword());

      TfsBeansHolder beans = new TfsBeansHolder(uri);
      Workspace[] workspaces = queryWorkspaces(authorizedCredentials, pi, beans);
      return new Tfs200xServerDescriptor(instanceId, authorizedCredentials, workspaces, beans);
    }
  }

  public static URI getBareUri(URI uri) {
    String path = uri.getPath();
    if (StringUtil.isNotEmpty(path) && !path.equals("/")) {
      // path with teamprojectcollection, leave just /tfs
      path = path.substring(0, path.lastIndexOf("/"));
      try {
        uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, null, null);
      }
      catch (URISyntaxException e) {
        LOG.error(e);
      }
    }
    return uri;
  }

  public static Workspace[] queryWorkspaces(Credentials authorizedCredentials,
                                            @Nullable ProgressIndicator pi, TfsBeansHolder beans)
    throws RemoteException, HostNotApplicableException {
    if (pi != null) {
      pi.setText(TFSBundle.message("loading.workspaces"));
    }

    QueryWorkspaces param = new QueryWorkspaces();
    param.setOwnerName(authorizedCredentials.getQualifiedUsername());
    param.setComputer(Workstation.getComputerName());
    return beans.getRepositoryStub(authorizedCredentials, pi).queryWorkspaces(param).getQueryWorkspacesResult().getWorkspace();
  }

  private static String getAuthorizedCredentialsFor200x(ConfigurationContext context, URI uri, Credentials credentials)
    throws RemoteException {
    ServerStatusStub serverStatusStub = new ServerStatusStub(context, TfsUtil.appendPath(uri, TFSConstants.SERVER_STATUS_ASMX));
    WebServiceHelper.setupStub(serverStatusStub, credentials, uri);

    CheckAuthenticationResponse response = serverStatusStub.checkAuthentication(new CheckAuthentication());
    return response.getCheckAuthenticationResult();
  }

  @Nullable
  private static String getPropertyValue(ArrayOfKeyValueOfStringString props, String propertyKey) {
    for (KeyValueOfStringString prop : props.getKeyValueOfStringString()) {
      if (propertyKey.equals(prop.getKey())) {
        return prop.getValue();
      }
    }
    return null;
  }

}
