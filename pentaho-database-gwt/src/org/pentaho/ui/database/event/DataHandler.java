package org.pentaho.ui.database.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.pentaho.database.model.DatabaseAccessType;
import org.pentaho.database.model.DatabaseConnection;
import org.pentaho.database.model.DatabaseConnectionPoolParameter;
import org.pentaho.database.model.IDatabaseConnection;
import org.pentaho.database.model.IDatabaseType;
import org.pentaho.database.model.PartitionDatabaseMeta;
import org.pentaho.database.util.DatabaseTypeHelper;
import org.pentaho.ui.database.event.ILaunch.Status;
import org.pentaho.ui.database.services.IXulAsyncDatabaseConnectionService;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulServiceCallback;
import org.pentaho.ui.xul.components.XulCheckbox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulListitem;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulDeck;
import org.pentaho.ui.xul.containers.XulDialog;
import org.pentaho.ui.xul.containers.XulListbox;
import org.pentaho.ui.xul.containers.XulRoot;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.containers.XulTreeItem;
import org.pentaho.ui.xul.containers.XulTreeRow;
import org.pentaho.ui.xul.containers.XulWindow;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.stereotype.Bindable;

/**
 * Handles all manipulation of the DatabaseMeta, data retrieval from XUL DOM and rudimentary validation.
 * 
 *  TODO:
 *  2. Needs to be abstracted away from the DatabaseMeta object, so other tools 
 *  in the platform can use the dialog and their preferred database object.
 *  3. Needs exception handling, string resourcing and logging
 *   
 * @author gmoran
 * @created Mar 19, 2008
 *
 */
public class DataHandler extends AbstractXulEventHandler {

  private static final String LINE_SEPARATOR = "\n"; // System.getProperty("line.separator"); //$NON-NLS-1$
  
  protected DatabaseDialogListener listener;
  protected IMessages messages;
  protected ILaunch launch;
  protected IXulAsyncDatabaseConnectionService connectionService;
  protected DatabaseTypeHelper databaseTypeHelper;
  protected IFragmentHandler fragmentHandler;

  private DatabaseConnectionPoolParameter[] poolingParameters;
  
  protected IDatabaseConnection databaseConnection = null;

  private IDatabaseConnection cache = new DatabaseConnection();

  private XulDeck dialogDeck;

  private XulListbox deckOptionsBox;

  private XulListbox connectionBox;

  private XulListbox accessBox;

  private XulTextbox connectionNameBox;

  protected XulTextbox hostNameBox;

  protected XulTextbox databaseNameBox;

  protected XulTextbox portNumberBox;

  protected XulTextbox userNameBox;

  protected XulTextbox passwordBox;

  // Generic database specific
  protected XulTextbox customDriverClassBox;

  // Generic database specific
  protected XulTextbox customUrlBox;

  // Oracle specific
  protected XulTextbox dataTablespaceBox;

  // Oracle specific
  protected XulTextbox indexTablespaceBox;

  // MS SQL Server specific
  private XulTextbox serverInstanceBox;

  // Informix specific
  private XulTextbox serverNameBox;

  // SAP R/3 specific
  protected XulTextbox languageBox;

  // SAP R/3 specific
  protected XulTextbox systemNumberBox;

  // SAP R/3 specific
  protected XulTextbox clientBox;

  // MS SQL Server specific
  private XulCheckbox doubleDecimalSeparatorCheck;
  private XulCheckbox useIntegratedSecurityCheck;

  // MySQL specific
  private XulCheckbox resultStreamingCursorCheck;

  // ==== Options Panel ==== //

  protected XulTree optionsParameterTree;

  // ==== Clustering Panel ==== //

  private XulCheckbox clusteringCheck;

  protected XulTree clusterParameterTree;

  private XulLabel clusterParameterDescriptionLabel;

  // ==== Advanced Panel ==== //

  XulCheckbox quoteIdentifiersCheck;

  XulCheckbox lowerCaseIdentifiersCheck;

  XulCheckbox upperCaseIdentifiersCheck;
  
  XulTextbox sqlBox;

  // ==== Pooling Panel ==== //

  private XulLabel poolSizeLabel;

  private XulLabel maxPoolSizeLabel;

  private XulCheckbox poolingCheck;

  protected XulTextbox poolSizeBox;

  protected XulTextbox maxPoolSizeBox;

  private XulTextbox poolingDescription;

  private XulLabel poolingParameterDescriptionLabel;

  private XulLabel poolingDescriptionLabel;

  protected XulTree poolParameterTree;

  public DataHandler() {
    setName("dataHandler"); //$NON-NLS-1$
  }
  
  public void setFragmentHandler(IFragmentHandler fragmentHandler) {
    this.fragmentHandler = fragmentHandler;
  }
  
  public void setDatabaseTypeHelper(DatabaseTypeHelper databaseTypeHelper) {
    this.databaseTypeHelper = databaseTypeHelper;
  }
  
  public void setDatabaseDialogListener(DatabaseDialogListener listener) {
    this.listener = listener;
  }

  public void setAsyncDatabaseConnectionService(IXulAsyncDatabaseConnectionService connectionService) {
    this.connectionService = connectionService;
  }
  
  public void setMessages(IMessages messages) {
    this.messages = messages;
  }
  
  public void setLaunch(ILaunch launch) {
    this.launch = launch;
  }
  
  @Bindable
  public void loadConnectionData() {

	// HACK: need to check if onload event was already fired. 
	// It is called from XulDatabaseDialog from dcDialog.getSwtInstance(shell); AND dialog.show();
	// Multiple calls lead to multiple numbers of database types.
	// Therefore we check if the connectionBox was already filled.
	if(connectionBox!=null) return;
	
    getControls();

    // Add sorted types to the listbox now.

    for (String key : databaseTypeHelper.getDatabaseTypeNames()) {
      connectionBox.addItem(key);
    }
    

    // HACK: Need to force height of list control, as it does not behave 
    // well when using relative layouting

    connectionBox.setRows(connectionBox.getRows());

    Object key = getSelectedString(connectionBox);

    // Nothing selected yet...select first item.

    // TODO Implement a connection type preference,
    // and use that type as the default for 
    // new databases.

    if (key == null) {
      key = databaseTypeHelper.getDatabaseTypeNames().get(0); // connectionMap.firstKey();
      connectionBox.setSelectedItem(key);
    } else {
      connectionBox.setSelectedItem(key);
    }

    // HACK: Need to force selection of first panel

    if (dialogDeck != null) {
      setDeckChildIndex();
    }

    setDefaultPoolParameters();
    
    if (databaseConnection != null) {
      setInfo(databaseConnection);
    }
  }

  //On Database type change
  @Bindable
  public void loadAccessData() {

    getControls();

    pushCache();

    String key = getSelectedString(connectionBox);
    
    // Nothing selected yet...
    if (key == null) {
      key = databaseTypeHelper.getDatabaseTypeNames().get(0);
      connectionBox.setSelectedItem(key);
      return;
    }

    // DatabaseInterface database = connectionMap.get(key);
    IDatabaseType database = databaseTypeHelper.getDatabaseTypeByName(key);

    List<DatabaseAccessType> acc = database.getSupportedAccessTypes();
    Object accessKey = getSelectedString(accessBox);
    accessBox.removeItems();

    // Add those access types applicable to this connection type

    for (DatabaseAccessType value : acc) {
      accessBox.addItem(value.getName());
    }

    // HACK: Need to force height of list control, as it does not behave 
    // well when using relative layouting

    accessBox.setRows(accessBox.getRows());

    // May not exist for this connection type.

    accessBox.setSelectedItem(accessKey);

    // Last resort, set first as default
    if (accessBox.getSelectedItem() == null) {
      accessBox.setSelectedItem(acc.get(0).getName());
    }
    
    setOptionsData(databaseConnection != null ? databaseConnection.getExtraOptions() : null);
    setClusterData(databaseConnection != null ? databaseConnection.getPartitioningInformation() : null);
    
    popCache();

  }

  private String getSelectedString(XulListbox box) {
    String key = null;
    Object keyObj = box.getSelectedItem();
    if (keyObj instanceof XulListitem) {
      key = (String)((XulListitem)keyObj).getLabel();
    } else {
      key = (String)keyObj;
    }
    return key;
  }
  
  @Bindable
  public void editOptions(int index) {
    if( index +1 == optionsParameterTree.getRows()){
      //editing last row add a new one below

      Object[][] values = optionsParameterTree.getValues();
      Object[] row = values[values.length-1];
      if(row != null && (!isEmpty((String)row[0]) || !isEmpty((String)row[1]))){
        //actually have something in current last row
        XulTreeRow newRow = optionsParameterTree.getRootChildren().addNewRow();

        newRow.addCellText(0, ""); //$NON-NLS-1$
        newRow.addCellText(1, ""); //$NON-NLS-1$
      }
    }
  }
  
  private boolean isEmpty(String str) {
    return str == null || str.trim().length() == 0;
  }

  @Bindable
  public void getOptionHelp() {

    String message = null;
    IDatabaseConnection database = new DatabaseConnection();

    getInfo(database);
    String url = database.getDatabaseType().getExtraOptionsHelpUrl();

    if ((url == null) || (url.trim().length() == 0)) {
      message = messages.getString("DataHandler.USER_NO_HELP_AVAILABLE"); //$NON-NLS-1$
      
      showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, false); //$NON-NLS-1$
      return;
    }

    if (launch != null) {
      Status status = launch.openUrl(url, messages);
  
      if (status.equals(Status.Failed)) {
        message = messages.getString("DataHandler.USER_UNABLE_TO_LAUNCH_BROWSER", url);  //$NON-NLS-1$
        showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, false); //$NON-NLS-1$
      }
    } else {
      showMessage(
          messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), //$NON-NLS-1$
          messages.getString("DataHandler.LAUNCH_NOT_SUPPORTED"), //$NON-NLS-1$
          false);
    }
  }

  public void addEmptyRowsToOptions() {
    Object[][] values = optionsParameterTree.getValues();
    int emptyRows = 0;
    int nonEmptyRows = 0;
    for (int i = 0; i < values.length; i++) {
      String parameter = (String)values[i][0];
      if ((parameter != null) && (parameter.trim().length() > 0)) {
        emptyRows = 0;
        nonEmptyRows++;
      } else {
        emptyRows++;
      }
    }
    if (emptyRows == 0) {
      int numToAdd = 5;
      if(nonEmptyRows > 0){
        numToAdd = 1;
      }
      while(numToAdd-- > 0){
        XulTreeRow row = optionsParameterTree.getRootChildren().addNewRow();
        //easy way of putting new cells in the row
        row.addCellText(0, ""); //$NON-NLS-1$
        row.addCellText(1, ""); //$NON-NLS-1$
      }
      optionsParameterTree.update();
    }
  }
  
  @Bindable
  public void setDeckChildIndex() {

    getControls();
    
    // if pooling selected, check the parameter validity before allowing 
    // a deck panel switch...
    int originalSelection = dialogDeck.getSelectedIndex();

    boolean passed = true;
    if (originalSelection == 3){
      passed = checkPoolingParameters();
    }
    
    if (originalSelection == 1) {
      addEmptyRowsToOptions();
    }
    
    if (passed) { 
      int selected = deckOptionsBox.getSelectedIndex();
      if (selected < 0 && deckOptionsBox.getRowCount() > 0) {
        selected = 0;
        deckOptionsBox.setSelectedIndex(0);
      }
      dialogDeck.setSelectedIndex(selected);
    }else{
      dialogDeck.setSelectedIndex(originalSelection);
      deckOptionsBox.setSelectedIndex(originalSelection);
    }

  }

  public void onPoolingCheck() {
    if (poolingCheck != null) {
      boolean dis = !poolingCheck.isChecked();
      if (poolSizeBox != null) {
        poolSizeBox.setDisabled(dis);
      }
      if (maxPoolSizeBox != null) {
        maxPoolSizeBox.setDisabled(dis);
      }
      if (poolSizeLabel != null) {
        poolSizeLabel.setDisabled(dis);
      }
      if (maxPoolSizeLabel != null) {
        maxPoolSizeLabel.setDisabled(dis);
      }
      if (poolParameterTree != null) {
        poolParameterTree.setDisabled(dis);
      }
      if (poolingParameterDescriptionLabel != null) {
        poolingParameterDescriptionLabel.setDisabled(dis);
      }
      if (poolingDescriptionLabel != null) {
        poolingDescriptionLabel.setDisabled(dis);
      }
      if (poolingDescription != null) {
        poolingDescription.setDisabled(dis);
      }

    }
  }

  public void onClusterCheck() {
    if (clusteringCheck != null) {
      boolean dis = !clusteringCheck.isChecked();
      if (clusterParameterTree != null) {
        clusterParameterTree.setDisabled(dis);
      }
      if(clusterParameterDescriptionLabel != null){
        clusterParameterDescriptionLabel.setDisabled(dis);
      }
    }
  }

  public Object getData() {

//    if (databaseMeta == null) {
//      databaseMeta = new DatabaseConnection();
//    }
//    
//    if (!windowClosed()){
//      this.getInfo(databaseMeta);
//    }
    return databaseConnection;
  }

  public void setData(Object data) {
    
    // if a null value is passed in, replace it with an 
    // empty database connection
    if (data == null) {
      data = new DatabaseConnection();
    }
    
    if (data instanceof DatabaseConnection) {
      databaseConnection = (IDatabaseConnection) data;
    }
    setInfo(databaseConnection);
  }

  public void pushCache() {
    getConnectionSpecificInfo(cache);
  }

  public void popCache() {
    setConnectionSpecificInfo(cache);
  }

  @Bindable
  public void onCancel() {
    close();
    if (listener != null) {
      listener.onDialogCancel();
    }
  }
  
  @Bindable
  private void close(){
  	XulComponent window = document.getElementById("general-datasource-window"); //$NON-NLS-1$
  	
  	if(window == null){ //window must be root
  		window = document.getRootElement();
  	}
    if(window instanceof XulDialog){
    	((XulDialog) window).hide();
    } else if(window instanceof XulWindow){
    	((XulWindow) window).close();
    }
  }
  
  private boolean windowClosed() {
    boolean closedWindow = true; 
    XulComponent window = document.getElementById("general-datasource-window"); //$NON-NLS-1$
    
    if(window == null){ //window must be root
      window = document.getRootElement();
    }
    if(window instanceof XulWindow){
      closedWindow =  ((XulWindow)window).isClosed();
    }
    return closedWindow;
  }

  @Bindable
  public void onOK() {

    IDatabaseConnection database = new DatabaseConnection();
    this.getInfo(database);

    boolean passed = checkPoolingParameters();
    if (!passed){
      return;
    }
    
    connectionService.checkParameters(database, new XulServiceCallback<List<String>>() {
      public void error(String message, Throwable error) {
        showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, message.length() > 300); //$NON-NLS-1$
      }
      public void success(List<String> remarks) {
        String message = ""; //$NON-NLS-1$
        if (remarks.size() != 0) {
          for (int i = 0; i < remarks.size(); i++) {
            message = message.concat("* ").concat(remarks.get(i)).concat(LINE_SEPARATOR); //$NON-NLS-1$
          }
          showMessage(messages.getString("DataHandler.CHECK_PARAMS_TITLE"), message, false); //$NON-NLS-1$
        } else {
          if (databaseConnection == null) {
            databaseConnection = new DatabaseConnection();
          }
          getInfo(databaseConnection);
          databaseConnection.setChanged(true);
          close();
          if (listener != null) {
            listener.onDialogAccept(databaseConnection);
          }
        }      
      }
    });
    
  }

  @Bindable
  public void testDatabaseConnection() {

    final IDatabaseConnection database = new DatabaseConnection();

    getInfo(database);
    connectionService.checkParameters(database, new XulServiceCallback<List<String>>() {
      public void error(String message, Throwable error) {
        showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, message.length() > 300); //$NON-NLS-1$
      }
      public void success(List<String> remarks) {
        String message = ""; //$NON-NLS-1$
        if (remarks.size() != 0) {
          for (int i = 0; i < remarks.size(); i++) {
            message = message.concat("* ").concat(remarks.get(i)).concat(LINE_SEPARATOR); //$NON-NLS-1$
          }
          showMessage(messages.getString("DataHandler.TEST_MESSAGE_TITLE"), message, message.length() > 300); //$NON-NLS-1$
        } else {
          connectionService.testConnection(database, new XulServiceCallback<String>() {
            public void error(String message, Throwable error) {
              showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, message.length() > 300); //$NON-NLS-1$
            }
            public void success(String message) {
              showMessage(messages.getString("DataHandler.TEST_MESSAGE_TITLE"), message, message.length() > 300);  //$NON-NLS-1$            
            }
          });
        }
      }
    });
  }

  protected void getInfo(IDatabaseConnection meta) {

    getControls();

    // TODO: WG: why is this necessary?
//    if (this.databaseMeta != null && this.databaseMeta != meta) {
//      meta.initializeVariablesFrom(this.databaseMeta);
//    }

    // Let's not remove any (default) options or attributes
    // We just need to display the correct ones for the database type below...
    //
    // In fact, let's just clear the database port...
    //
    // TODO: what about the port number?

    // Name:
    meta.setName(connectionNameBox.getValue());

    // Connection type:
    Object connection = getSelectedString(connectionBox);
    if (connection != null) {
      meta.setDatabaseType(databaseTypeHelper.getDatabaseTypeByName((String) connection));
    }

    // Access type:
    Object access = getSelectedString(accessBox);
    if (access != null) {
      meta.setAccessType(DatabaseAccessType.getAccessTypeByName((String) access));
    }

    getConnectionSpecificInfo(meta);

    // Port number:
    if (portNumberBox != null) {
      meta.setDatabasePort(portNumberBox.getValue());
    }

    // Option parameters: 

    if (optionsParameterTree != null) {
      Object[][] values = optionsParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        String parameter = (String) values[i][0];
        String value = (String) values[i][1];

        if (value == null) {
          value = ""; //$NON-NLS-1$
        }

        //int dbType = meta.getDatabaseType();

        // Only if parameter are supplied, we will add to the map...
        if ((parameter != null) && (parameter.trim().length() > 0)) {
          if (value.trim().length() <= 0) {
            value = DatabaseConnection.EMPTY_OPTIONS_STRING;
          }
          meta.addExtraOption(meta.getDatabaseType().getShortName(), parameter, value);
        }
      }
    }

    // Advanced panel settings:

    if (quoteIdentifiersCheck != null) {
      meta.setQuoteAllFields(quoteIdentifiersCheck.isChecked());
    }

    if (lowerCaseIdentifiersCheck != null) {
      meta.setForcingIdentifiersToLowerCase(lowerCaseIdentifiersCheck.isChecked());
    }

    if (upperCaseIdentifiersCheck != null) {
      meta.setForcingIdentifiersToUpperCase(upperCaseIdentifiersCheck.isChecked());
    }

    if (sqlBox != null) {
        meta.setConnectSql(sqlBox.getValue());
    }

    // Cluster panel settings
    if (clusteringCheck != null) {
      meta.setPartitioned(clusteringCheck.isChecked());
    }

    if ((clusterParameterTree != null) && (meta.isPartitioned())) {

      Object[][] values = clusterParameterTree.getValues();
      List<PartitionDatabaseMeta> pdms = new ArrayList<PartitionDatabaseMeta>();
      for (int i = 0; i < values.length; i++) {

        String partitionId = (String) values[i][0];

        if ((partitionId == null) || (partitionId.trim().length() <= 0)) {
          continue;
        }

        String hostname = (String) values[i][1];
        String port = (String) values[i][2];
        String dbName = (String) values[i][3];
        String username = (String) values[i][4];
        String password = (String) values[i][5];
        PartitionDatabaseMeta pdm = new PartitionDatabaseMeta(partitionId, hostname, port, dbName);
        pdm.setUsername(username);
        pdm.setPassword(password);
        pdms.add(pdm);
      }
      meta.setPartitioningInformation(pdms);
    }

    if (poolingCheck != null) {
      meta.setUsingConnectionPool(poolingCheck.isChecked());
    }

    if (meta.isUsingConnectionPool()) {
      if (poolSizeBox != null) {
        try {
          int initialPoolSize = Integer.parseInt(poolSizeBox.getValue());
          meta.setInitialPoolSize(initialPoolSize);
        } catch (NumberFormatException e) {
          // TODO log exception and move on ...
        }
      }

      if (maxPoolSizeBox != null) {
        try {
          int maxPoolSize = Integer.parseInt(maxPoolSizeBox.getValue());
          meta.setMaximumPoolSize(maxPoolSize);
        } catch (NumberFormatException e) {
          // TODO log exception and move on ...
        }
      }

      if (poolParameterTree != null) {
        Object[][] values = poolParameterTree.getValues();
        Map<String, String> properties = new HashMap<String, String>();
        for (int i = 0; i < values.length; i++) {

          boolean isChecked = false;
          if (values[i][0] instanceof Boolean){
            isChecked = ((Boolean)values[i][0]).booleanValue();
          }else{
            isChecked = Boolean.valueOf((String) values[i][0]);
          }

          if (!isChecked) {
            continue;
          }

          String parameter = (String) values[i][1];
          String value = (String) values[i][2];
          if ((parameter != null) && (parameter.trim().length() > 0) && (value != null) && (value.trim().length() > 0)) {
            properties.put(parameter, value);
          }

        }
        meta.setConnectionPoolingProperties(properties);
      }
    }

  }

  private void setInfo(final IDatabaseConnection meta) {

    if (meta == null) {
      return;
    }

    getControls();

    // TODO: Delete method: copyConnectionSpecificInfo(meta, cache);
    
    // Name:
    connectionNameBox.setValue(meta.getName());

    // disable refresh for now
    fragmentHandler.setDisableRefresh(true);
    
    // Connection type:
    if (meta.getDatabaseType() != null) {
      connectionBox.setSelectedItem(meta.getDatabaseType().getName());
    } else {
      connectionBox.setSelectedIndex(0);
    }

    // Access type:
    if (meta.getAccessType() != null) {
      accessBox.setSelectedItem(meta.getAccessType().getName());
    } else {
      accessBox.setSelectedIndex(0);
    }

    // this is broken out so we can set the cache information only when caching 
    // connection values
    fragmentHandler.refreshOptionsWithCallback(new IFragmentHandler.Callback() {
      public void callback() {
        fragmentHandler.setDisableRefresh(false);
        setConnectionSpecificInfo(meta);
      }
    });

    // Port number:
    if (portNumberBox != null) {
      portNumberBox.setValue(meta.getDatabasePort());
    }

    // Options Parameters:

    setOptionsData(meta.getExtraOptions());

    // Advanced panel settings:

    if (quoteIdentifiersCheck != null) {
      quoteIdentifiersCheck.setChecked(meta.isQuoteAllFields());
    }

    if (lowerCaseIdentifiersCheck != null) {
      lowerCaseIdentifiersCheck.setChecked(meta.isForcingIdentifiersToLowerCase());
    }

    if (upperCaseIdentifiersCheck != null) {
      upperCaseIdentifiersCheck.setChecked(meta.isForcingIdentifiersToUpperCase());
    }

    if (sqlBox != null) {
      sqlBox.setValue(meta.getConnectSql() == null ? "" : meta.getConnectSql()); //$NON-NLS-1$
    }

    // Clustering panel settings

    if (clusteringCheck != null) {
      clusteringCheck.setChecked(meta.isPartitioned());
    }

    setClusterData(meta.getPartitioningInformation());

    // Pooling panel settings 

    if (poolingCheck != null) {
      poolingCheck.setChecked(meta.isUsingConnectionPool());
    }

    if (meta.isUsingConnectionPool()) {
      if (poolSizeBox != null) {
        poolSizeBox.setValue(Integer.toString(meta.getInitialPoolSize()));
      }

      if (maxPoolSizeBox != null) {
        maxPoolSizeBox.setValue(Integer.toString(meta.getMaximumPoolSize()));
      }

      setPoolProperties(meta.getConnectionPoolingProperties());
    }

    dialogDeck.setSelectedIndex(0);
    deckOptionsBox.setSelectedIndex(0);

    setDeckChildIndex();
    onPoolingCheck();
    onClusterCheck();
    
  }

  /**
   * 
   * @return the list of parameters that were enabled, but had invalid 
   * return values (null or empty)
   */
  private boolean checkPoolingParameters(){
    
    List <String> returnList = new ArrayList <String>();
    if (poolParameterTree != null) {
      Object[][] values = poolParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        boolean isChecked = false;
        if (values[i][0] instanceof Boolean){
          isChecked = ((Boolean)values[i][0]).booleanValue();
        }else{
          isChecked = Boolean.valueOf((String) values[i][0]);
        }

        if (!isChecked) {
          continue;
        }

        String parameter = (String) values[i][1];
        String value = (String) values[i][2];
        if ((value == null) || (value.trim().length() <= 0)) {
          returnList.add(parameter);
        }

      }
      if (returnList.size() > 0){
        String parameters = LINE_SEPARATOR;
        for (String parameter : returnList){
          parameters = parameters.concat(parameter).concat(LINE_SEPARATOR);
        }
        
        String message = messages.getString("DataHandler.USER_INVALID_PARAMETERS").concat(parameters); //$NON-NLS-1$
        showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, false); //$NON-NLS-1$
      }
    }
    return returnList.size() <= 0;
  }

  private void setPoolProperties(Map<String, String> properties) {
    if (poolParameterTree != null) {
      Object[][] values = poolParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        String parameter = (String) values[i][1];
        boolean isChecked = properties.containsKey(parameter);

        if (!isChecked) {
          continue;
        }
        XulTreeItem item = poolParameterTree.getRootChildren().getItem(i);
        item.getRow().addCellText(0, "true"); // checks the checkbox //$NON-NLS-1$

        String value = properties.get(parameter);
        item.getRow().addCellText(2, value);

      }
    }

  }
  
  @Bindable
  public void restoreDefaults() {
    if (poolingParameters != null && poolParameterTree != null) {
      for (int i = 0; i < poolParameterTree.getRootChildren().getItemCount(); i++){
        XulTreeItem item = poolParameterTree.getRootChildren().getItem(i);
        String parameterName = item.getRow().getCell(1).getLabel();
        String defaultValue = DatabaseConnectionPoolParameter.findParameter(parameterName, poolingParameters).getDefaultValue();
        if ((defaultValue == null) || (defaultValue.trim().length()<=0)){
          continue;
        }
        item.getRow().addCellText(2, defaultValue);
      }
    }
    
  }
  
  private void setDefaultPoolParameters() {
    connectionService.getPoolingParameters(new XulServiceCallback<DatabaseConnectionPoolParameter[]>() {
      public void error(String message, Throwable error) {
        showMessage(messages.getString("DataHandler.ERROR_MESSAGE_TITLE"), message, false); //$NON-NLS-1$
      }
      public void success(DatabaseConnectionPoolParameter[] retVal) {
        poolingParameters = retVal;
        if (poolParameterTree != null) {
          for (DatabaseConnectionPoolParameter parameter : poolingParameters) {
            XulTreeRow row = poolParameterTree.getRootChildren().addNewRow();
            row.addCellText(0, "false"); //$NON-NLS-1$
            row.addCellText(1, parameter.getParameter());
            row.addCellText(2, parameter.getDefaultValue());
          }
        }
        
        // HACK: reDim the pooling table
        if(poolParameterTree != null) {
          poolParameterTree.setRows(poolParameterTree.getRows());
        }
      }
    });
  }
  
  private void clearOptions() {
    Object[][] values = optionsParameterTree.getValues();
    for (int i = values.length - 1; i >= 0; i--) {
      optionsParameterTree.getRootChildren().removeItem(i);
    }
  }

  private void setOptionsData(Map<String, String> extraOptions) {

    if (optionsParameterTree == null) {
      return;
    }
    clearOptions();
    if(extraOptions != null) {
      Iterator<String> keys = extraOptions.keySet().iterator();
      String connection = getSelectedString(connectionBox);
      IDatabaseType currentType = null;
      
      if(connection != null){
        currentType = databaseTypeHelper.getDatabaseTypeByName(connection);
      }
      
      while (keys.hasNext()) {

        String parameter = keys.next();
        String value = extraOptions.get(parameter);
        if ((value == null) || (value.trim().length() <= 0) || (value.equals(DatabaseConnection.EMPTY_OPTIONS_STRING))) {
          value = ""; //$NON-NLS-1$
        }

        // If the parameter starts with a database type code we show it in the options, otherwise we don't.
        // For example MySQL.defaultFetchSize
        //

        int dotIndex = parameter.indexOf('.');
        if (dotIndex >= 0) {
          String parameterOption = parameter.substring(dotIndex + 1);
          String databaseTypeString = parameter.substring(0,dotIndex);
          IDatabaseType databaseType = databaseTypeHelper.getDatabaseTypeByShortName(databaseTypeString);
          if (currentType == databaseType) {
	          XulTreeRow row = optionsParameterTree.getRootChildren().addNewRow();
	          row.addCellText(0, parameterOption);
	          row.addCellText(1, value);
          }
        }
      }
      
    }
    // Add 5 blank rows if none are already there, otherwise, just add one.
    int numToAdd = 5;
    if(extraOptions != null && extraOptions.keySet().size() > 0){
      numToAdd = 1;
    }
    while(numToAdd-- > 0){
      XulTreeRow row = optionsParameterTree.getRootChildren().addNewRow();
      //easy way of putting new cells in the row
      row.addCellText(0, ""); //$NON-NLS-1$
      row.addCellText(1, ""); //$NON-NLS-1$
    }
    
    optionsParameterTree.update();
    
  }

  private void setClusterData(List<PartitionDatabaseMeta> clusterInformation) {

  	if (clusterParameterTree == null) {
  		// there's nothing to do 
  		return;
  	}
  	
    if ((clusterInformation != null) && (clusterParameterTree != null)) {

      for (int i = 0; i < clusterInformation.size(); i++) {

        PartitionDatabaseMeta meta = clusterInformation.get(i);
        XulTreeRow row = clusterParameterTree.getRootChildren().addNewRow();
        row.addCellText(0, meta.getPartitionId() == null ? "" : meta.getPartitionId()); //$NON-NLS-1$
        row.addCellText(1, meta.getHostname() == null ? "" : meta.getHostname()); //$NON-NLS-1$
        row.addCellText(2, meta.getPort() == null ? "" : meta.getPort()); //$NON-NLS-1$
        row.addCellText(3, meta.getDatabaseName() == null ? "" : meta.getDatabaseName()); //$NON-NLS-1$
        row.addCellText(4, meta.getUsername() == null ? "" : meta.getUsername()); //$NON-NLS-1$
        row.addCellText(5, meta.getPassword() == null ? "" : meta.getPassword()); //$NON-NLS-1$
      }
    }
    // Add 5 blank rows if none are already there, otherwise, just add one.
    int numToAdd = 5;
    if(clusterInformation != null && clusterInformation.size() > 0){
      numToAdd = 1;
    }
    while(numToAdd-- > 0){
      XulTreeRow row = clusterParameterTree.getRootChildren().addNewRow();
      //easy way of putting new cells in the row
      row.addCellText(0, ""); //$NON-NLS-1$
      row.addCellText(1, ""); //$NON-NLS-1$
      row.addCellText(2, ""); //$NON-NLS-1$
      row.addCellText(3, ""); //$NON-NLS-1$
      row.addCellText(4, ""); //$NON-NLS-1$
      row.addCellText(5, ""); //$NON-NLS-1$
    }
  }

  @Bindable
  public void poolingRowChange(int idx) {
    if (poolingParameters != null) {
      if (idx != -1) {
  
        if (idx >= poolingParameters.length) {
          idx = poolingParameters.length - 1;
        }
        if (idx < 0) {
          idx = 0;
        }
        poolingDescription.setValue(poolingParameters[idx].getDescription());
        
        XulTreeRow row = poolParameterTree.getRootChildren().getItem(idx).getRow();
        if (row.getSelectedColumnIndex() == 2){
          row.addCellText(0, "true"); //$NON-NLS-1$
        }
      }
    }
  }

  private void copyConnectionSpecificInfo(IDatabaseConnection from, IDatabaseConnection to) {
    // Hostname:
    if (from.getHostname() != null) {
      to.setHostname(from.getHostname());
    }

    // Database name:
    if (from.getDatabaseName() != null) {
      to.setDatabaseName(from.getDatabaseName());
    }

    // Username:
    if (from.getUsername() != null) {
      to.setUsername(from.getUsername());
    }

    // Password:
    if (from.getPassword() != null) {
      to.setPassword(from.getPassword());
    }

    // Streaming result cursor:
    to.setStreamingResults(from.isStreamingResults());

    // Data tablespace:
    if (from.getDataTablespace() != null) {
      to.setDataTablespace(from.getDataTablespace());
    }

    // Index tablespace
    if (from.getIndexTablespace() != null) {
      to.setIndexTablespace(from.getIndexTablespace());
    }

    // The SQL Server instance name overrides the option.
    // Empty doesn't clear the option, we have mercy.

    if (from.getSQLServerInstance() != null) {
      if (from.getSQLServerInstance().trim().length() > 0) {
        to.addExtraOption("MSSQL", "instance", from.getSQLServerInstance());
        // meta.setSQLServerInstance(serverInstanceBox.getValue());
      }
    }

    // SQL Server double decimal separator
    to.setUsingDoubleDecimalAsSchemaTableSeparator(from.isUsingDoubleDecimalAsSchemaTableSeparator());

    // SAP Attributes...
    if (from.getAttributes().get("SAPLanguage") != null) {
      to.getAttributes().put("SAPLanguage", from.getAttributes().get("SAPLanguage"));
    }
    if (from.getAttributes().get("SAPSystemNumber") != null) {
      to.getAttributes().put("SAPSystemNumber", from.getAttributes().get("SAPSystemNumber"));
    }
    if (from.getAttributes().get("SAPClient") != null) {
      to.getAttributes().put("SAPClient", from.getAttributes().get("SAPClient"));
    }

    // Generic settings...
    if (from.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_URL) != null) {
      to.getAttributes().put(DatabaseConnection.ATTRIBUTE_CUSTOM_URL, from.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_URL));
    }
    
    if (from.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_DRIVER_CLASS) != null) {
      to.getAttributes().put(DatabaseConnection.ATTRIBUTE_CUSTOM_DRIVER_CLASS, from.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_DRIVER_CLASS));
    }
    
    if (from.getInformixServername() != null) {
      to.setInformixServername(from.getInformixServername());
    }
  }
  
  private void getConnectionSpecificInfo(IDatabaseConnection meta) {
    // Hostname:
    if (hostNameBox != null) {
      meta.setHostname(hostNameBox.getValue());
    }

    // Database name:
    if (databaseNameBox != null) {
      meta.setDatabaseName(databaseNameBox.getValue());
    }

    // Username:
    if (userNameBox != null) {
      meta.setUsername(userNameBox.getValue());
    }

    // Password:
    if (passwordBox != null) {
      meta.setPassword(passwordBox.getValue());
    }

    // Streaming result cursor:
    if (resultStreamingCursorCheck != null) {
      meta.setStreamingResults(resultStreamingCursorCheck.isChecked());
    }

    // Data tablespace:
    if (dataTablespaceBox != null) {
      meta.setDataTablespace(dataTablespaceBox.getValue());
    }

    // Index tablespace
    if (indexTablespaceBox != null) {
      meta.setIndexTablespace(indexTablespaceBox.getValue());
    }

    // The SQL Server instance name overrides the option.
    // Empty doesn't clear the option, we have mercy.

    if (serverInstanceBox != null) {
      if (serverInstanceBox.getValue() != null && 
          serverInstanceBox.getValue().trim().length() > 0) {
        meta.addExtraOption("MSSQL", "instance", serverInstanceBox.getValue());
        // meta.setSQLServerInstance(serverInstanceBox.getValue());
      }
    }

    // SQL Server double decimal separator
    if (doubleDecimalSeparatorCheck != null) {
      meta.setUsingDoubleDecimalAsSchemaTableSeparator(doubleDecimalSeparatorCheck.isChecked());
    }
    
    if (useIntegratedSecurityCheck != null) {
      meta.getAttributes().put("MSSQLUseIntegratedSecurity", ""+useIntegratedSecurityCheck.isChecked());
    }

    // SAP Attributes...
    if (languageBox != null) {
      meta.getAttributes().put("SAPLanguage", languageBox.getValue());
    }
    if (systemNumberBox != null) {
      meta.getAttributes().put("SAPSystemNumber", systemNumberBox.getValue());
    }
    if (clientBox != null) {
      meta.getAttributes().put("SAPClient", clientBox.getValue());
    }

    // Generic settings...
    if (customUrlBox != null) {
      meta.getAttributes().put(DatabaseConnection.ATTRIBUTE_CUSTOM_URL, customUrlBox.getValue());
    }
    if (customDriverClassBox != null) {
      meta.getAttributes().put(DatabaseConnection.ATTRIBUTE_CUSTOM_DRIVER_CLASS, customDriverClassBox.getValue());
    }

    // Server Name:  (Informix)
    if (serverNameBox != null) {
      meta.setInformixServername(serverNameBox.getValue());
    }

  }

  private void setConnectionSpecificInfo(IDatabaseConnection meta) {

    getControls();

    if (hostNameBox != null) {
      hostNameBox.setValue(meta.getHostname());
    }

    // Database name:
    if (databaseNameBox != null) {
      databaseNameBox.setValue(meta.getDatabaseName());
    }

    // Username:
    if (userNameBox != null) {
      userNameBox.setValue(meta.getUsername());
    }

    // Password:
    if (passwordBox != null) {
      passwordBox.setValue(meta.getPassword());
    }

    // Streaming result cursor:
    if (resultStreamingCursorCheck != null) {
      resultStreamingCursorCheck.setChecked(meta.isStreamingResults());
    }

    // Data tablespace:
    if (dataTablespaceBox != null) {
      dataTablespaceBox.setValue(meta.getDataTablespace());
    }

    // Index tablespace
    if (indexTablespaceBox != null) {
      indexTablespaceBox.setValue(meta.getIndexTablespace());
    }

    if (serverInstanceBox != null) {
      serverInstanceBox.setValue(meta.getSQLServerInstance());
    }

    // SQL Server double decimal separator
    if (doubleDecimalSeparatorCheck != null) {
      doubleDecimalSeparatorCheck.setChecked(meta.isUsingDoubleDecimalAsSchemaTableSeparator());
    }

    if (useIntegratedSecurityCheck != null) {
      useIntegratedSecurityCheck.setChecked("true".equals(meta.getAttributes().get("MSSQLUseIntegratedSecurity")));
    }
    
    // SAP Attributes...
    if (languageBox != null) {
      languageBox.setValue(meta.getAttributes().get("SAPLanguage"));
    }
    if (systemNumberBox != null) {
      systemNumberBox.setValue(meta.getAttributes().get("SAPSystemNumber"));
    }
    if (clientBox != null) {
      clientBox.setValue(meta.getAttributes().get("SAPClient"));
    }

    // Generic settings...
    if (customUrlBox != null) {
      customUrlBox.setValue(meta.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_URL));
    }
    if (customDriverClassBox != null) {
      customDriverClassBox
          .setValue(meta.getAttributes().get(DatabaseConnection.ATTRIBUTE_CUSTOM_DRIVER_CLASS));
    }

    // Server Name:  (Informix)
    if (serverNameBox != null) {
      serverNameBox.setValue(meta.getInformixServername());
    }

  }

  protected void getControls() {

    // Not all of these controls are created at the same time.. that's OK, for now, just check
    // each one for null before using.

    dialogDeck = (XulDeck) document.getElementById("dialog-panel-deck"); //$NON-NLS-1$
    deckOptionsBox = (XulListbox) document.getElementById("deck-options-list"); //$NON-NLS-1$
    connectionBox = (XulListbox) document.getElementById("connection-type-list"); //$NON-NLS-1$
    accessBox = (XulListbox) document.getElementById("access-type-list"); //$NON-NLS-1$
    connectionNameBox = (XulTextbox) document.getElementById("connection-name-text"); //$NON-NLS-1$
    hostNameBox = (XulTextbox) document.getElementById("server-host-name-text"); //$NON-NLS-1$
    databaseNameBox = (XulTextbox) document.getElementById("database-name-text"); //$NON-NLS-1$
    portNumberBox = (XulTextbox) document.getElementById("port-number-text"); //$NON-NLS-1$
    userNameBox = (XulTextbox) document.getElementById("username-text"); //$NON-NLS-1$
    passwordBox = (XulTextbox) document.getElementById("password-text"); //$NON-NLS-1$
    dataTablespaceBox = (XulTextbox) document.getElementById("data-tablespace-text"); //$NON-NLS-1$
    indexTablespaceBox = (XulTextbox) document.getElementById("index-tablespace-text"); //$NON-NLS-1$
    serverInstanceBox = (XulTextbox) document.getElementById("instance-text"); //$NON-NLS-1$
    serverNameBox = (XulTextbox) document.getElementById("server-name-text"); //$NON-NLS-1$
    customUrlBox = (XulTextbox) document.getElementById("custom-url-text"); //$NON-NLS-1$
    customDriverClassBox = (XulTextbox) document.getElementById("custom-driver-class-text"); //$NON-NLS-1$
    languageBox = (XulTextbox) document.getElementById("language-text"); //$NON-NLS-1$
    systemNumberBox = (XulTextbox) document.getElementById("system-number-text"); //$NON-NLS-1$
    clientBox = (XulTextbox) document.getElementById("client-text"); //$NON-NLS-1$
    doubleDecimalSeparatorCheck = (XulCheckbox) document.getElementById("decimal-separator-check"); //$NON-NLS-1$
    useIntegratedSecurityCheck = (XulCheckbox) document.getElementById("use-integrated-security-check"); //$NON-NLS-1$
    resultStreamingCursorCheck = (XulCheckbox) document.getElementById("result-streaming-check"); //$NON-NLS-1$
    poolingCheck = (XulCheckbox) document.getElementById("use-pool-check"); //$NON-NLS-1$
    clusteringCheck = (XulCheckbox) document.getElementById("use-cluster-check"); //$NON-NLS-1$
    clusterParameterDescriptionLabel = (XulLabel) document.getElementById("cluster-parameter-description-label"); //$NON-NLS-1$
    poolSizeLabel = (XulLabel) document.getElementById("pool-size-label"); //$NON-NLS-1$
    poolSizeBox = (XulTextbox) document.getElementById("pool-size-text"); //$NON-NLS-1$
    maxPoolSizeLabel = (XulLabel) document.getElementById("max-pool-size-label"); //$NON-NLS-1$
    maxPoolSizeBox = (XulTextbox) document.getElementById("max-pool-size-text"); //$NON-NLS-1$
    poolParameterTree = (XulTree) document.getElementById("pool-parameter-tree"); //$NON-NLS-1$
    clusterParameterTree = (XulTree) document.getElementById("cluster-parameter-tree"); //$NON-NLS-1$
    optionsParameterTree = (XulTree) document.getElementById("options-parameter-tree"); //$NON-NLS-1$
    poolingDescription = (XulTextbox) document.getElementById("pooling-description"); //$NON-NLS-1$ 
    poolingParameterDescriptionLabel = (XulLabel) document.getElementById("pool-parameter-description-label"); //$NON-NLS-1$ 
    poolingDescriptionLabel = (XulLabel) document.getElementById("pooling-description-label"); //$NON-NLS-1$ 
    quoteIdentifiersCheck = (XulCheckbox) document.getElementById("quote-identifiers-check"); //$NON-NLS-1$;
    lowerCaseIdentifiersCheck = (XulCheckbox) document.getElementById("force-lower-case-check"); //$NON-NLS-1$;
    upperCaseIdentifiersCheck = (XulCheckbox) document.getElementById("force-upper-case-check"); //$NON-NLS-1$;
    sqlBox = (XulTextbox) document.getElementById("sql-text"); //$NON-NLS-1$;
  }

  private void showMessage(String title, String message, boolean scroll){
    try{
      XulMessageBox box = (XulMessageBox) document.createElement("messagebox"); //$NON-NLS-1$
      box.setTitle(title);
      box.setMessage(message);
      box.setModalParent( ((XulRoot)document.getElementById("general-datasource-window")).getRootObject()); //$NON-NLS-1$
      if(scroll){
        box.setScrollable(true);
        box.setWidth(500);
        box.setHeight(400);
      }
      box.open();
    } catch(XulException e){
      System.out.println("Error creating messagebox "+e.getMessage());
      e.printStackTrace();
    }
  }
  
  public void handleUseSecurityCheckbox() {
    if(useIntegratedSecurityCheck != null) {
      if(useIntegratedSecurityCheck.isChecked()) {
        userNameBox.setDisabled(true);
        passwordBox.setDisabled(true);
      } else {
        userNameBox.setDisabled(false);
        passwordBox.setDisabled(false);        
      }
    }
  }

  @Bindable
  public void showContextHelp(){
    jsni_showContextHelp();
  }

  private native void jsni_showContextHelp()/*-{
    $wnd.open($wnd.CONTEXT_PATH+"webHelp/Viewer.jsp?topic=webHelp/concept_adding_a_jdbc_driver.html","webHelp","width=475,height=600,location=no,status=no,toolbar=no");
  }-*/;
}
