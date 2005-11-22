package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author max
 */
public class InspectionProfileImpl implements InspectionProfile.ModifiableModel, InspectionProfile {
  @NonNls public static final InspectionProfileImpl EMPTY_PROFILE = new InspectionProfileImpl("Default");
  @NonNls public static final InspectionProfileImpl DEFAULT_PROFILE = new InspectionProfileImpl("Default");
  static {
    final InspectionTool[] inspectionTools = DEFAULT_PROFILE.getInspectionTools();
    for (InspectionTool tool : inspectionTools) {
      final String shortName = tool.getShortName();
      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key == null){
        if (tool instanceof LocalInspectionToolWrapper) {
          key = HighlightDisplayKey.register(shortName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
        } else {
          key = HighlightDisplayKey.register(shortName);
        }
      }
      DEFAULT_PROFILE.myDisplayLevelMap.put(key, new ToolState(tool.getDefaultLevel(), tool.isEnabledByDefault()));
    }
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  @NonNls private static String VALID_VERSION = "1.0";
  private String myName;
  private File myFile;

  private HashMap<String, InspectionTool> myTools = new HashMap<String, InspectionTool>();
  private InspectionProfileManager myManager;

  //diff map with base profile
  private LinkedHashMap<HighlightDisplayKey, ToolState> myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>();

  private InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  private UnusedSymbolSettings myUnusedSymbolSettings = new UnusedSymbolSettings();
  @NonNls private static final String BASE_PROFILE_ATTR = "base_profile";
  @NonNls private static final String PROFILE_NAME_TAG = "profile_name";
  @NonNls private static final String VERSION_TAG = "version";
  @NonNls private static final String INSPECTION_TOOL_TAG = "inspection_tool";
  @NonNls private static final String ADDITIONAL_JAVADOC_TAGS_TAG = "ADDITIONAL_JAVADOC_TAGS";
  @NonNls private static final String ADDITIONAL_HTML_TAGS_TAG = "ADDITIONAL_HTML_TAGS";
  @NonNls private static final String VALUE_TAG = "value";
  @NonNls private static final String ADDITIONAL_HTML_ATTRIBUTES_TAG = "ADDITIONAL_HTML_ATTRIBUTES";
  @NonNls private static final String ADDITIONAL_REQUIRED_HTML_ATTRIBUTES_TAG = "ADDITIONAL_REQUIRED_HTML_ATTRIBUTES";
  @NonNls private static final String UNUSED_SYMBOL_SETTINGS_TAG = "UNUSED_SYMBOL_SETTINGS";
  @NonNls private static final String ENABLED_TAG = "enabled";
  @NonNls private static final String LEVEL_TAG = "level";
  @NonNls private static final String CLASS_TAG = "class";
  @NonNls private static final String ROOT_ELEMENT_TAG = "inspections";
  @NonNls private static final String CONFIG_FILE_EXTENSION = ".xml";
  //private String myBaseProfileName;

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  private boolean myModified = false;
  private boolean myInitialized = false;

  private VisibleTreeState myVisibleTreeState = new VisibleTreeState();

  private String myAdditionalJavadocTags = "";
  
  @NonNls private static final String DEFAULT_ADDITIONAL_HTML_TAGS="embed,nobr";
  @NonNls private static final String DEFAULT_ADDITIONAL_HTML_ATTRIBUTES="type,wmode,src,width,height";
  
  private String myAdditionalHtmlTags = DEFAULT_ADDITIONAL_HTML_TAGS;
  private String myAdditionalHtmlAttributes = DEFAULT_ADDITIONAL_HTML_ATTRIBUTES;
  private String myAdditionalRequiredHtmlAttributes = "";

  public InspectionProfileImpl(File file, InspectionProfileManager manager) throws IOException, JDOMException {
    this(getProfileName(file), getBaseProfileName(file), file, manager);
    mySource = null;
  }

  public InspectionProfileImpl(String name, String baseProfileName, File file, InspectionProfileManager manager) {
    myName = name;
    myFile = file;
    myManager = manager;
    if (baseProfileName != null) {
      myBaseProfile = manager.getProfile(baseProfileName);
      if (myBaseProfile == null) {//was not init yet
        myBaseProfile = new InspectionProfileImpl(baseProfileName, manager);
      }
    }
    mySource = null;
  }

  public InspectionProfileImpl(String name, InspectionProfileManager manager) {
    myName = name;
    myFile = new File(InspectionProfileManager.getProfileDirectory(), myName + CONFIG_FILE_EXTENSION);
    myManager = manager;
    mySource = null;
  }


  InspectionProfileImpl(InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.getName();
    myFile = inspectionProfile.getFile();
    myManager = inspectionProfile.getManager();
    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(inspectionProfile.myDisplayLevelMap);
    myTools = new HashMap<String, InspectionTool>();//new HashMap<String, InspectionTool>(inspectionProfile.myTools);
    myVisibleTreeState = new VisibleTreeState(inspectionProfile.myVisibleTreeState);

    myAdditionalJavadocTags = inspectionProfile.myAdditionalJavadocTags;
    myAdditionalHtmlTags = inspectionProfile.myAdditionalHtmlTags;
    myAdditionalHtmlAttributes = inspectionProfile.myAdditionalHtmlAttributes;
    myAdditionalRequiredHtmlAttributes = inspectionProfile.myAdditionalRequiredHtmlAttributes;

    myUnusedSymbolSettings = inspectionProfile.myUnusedSymbolSettings.copySettings();
    myBaseProfile = inspectionProfile.myBaseProfile;
    mySource = inspectionProfile;
  }

  //creates empty profile
  public InspectionProfileImpl(final String inspectionProfile) {
    myName = inspectionProfile;
    myInitialized = true;
    setDefaultErrorLevels();
  }

  public InspectionProfile getParentProfile() {
    return mySource;
  }

  public String getBaseProfileName() {
    if (myBaseProfile == null) return null;
    return myBaseProfile.getName();
  }

  public void setBaseProfile(InspectionProfileImpl profile) {
    myBaseProfile = profile;
  }

  public boolean isChanged() {
    return myModified;
  }

  public VisibleTreeState getExpandedNodes() {
    return myVisibleTreeState;
  }

  private static boolean toolSettingsAreEqual(HighlightDisplayKey key,
                                       InspectionProfileImpl profile1,
                                       InspectionProfileImpl profile2) {
    final String toolName = key.toString();
    final InspectionTool tool1 = profile1.getInspectionTool(toolName);//findInspectionToolByName(profile1, toolDisplayName);
    final InspectionTool tool2 = profile2.getInspectionTool(toolName);//findInspectionToolByName(profile2, toolDisplayName);
    if (tool1 == null && tool2 == null) {
      return true;
    }
    if (tool1 != null && tool2 != null) {
      try {
        @NonNls String tempRoot = "root";
        Element oldToolSettings = new Element(tempRoot);
        tool1.writeExternal(oldToolSettings);
        Element newToolSettings = new Element(tempRoot);
        tool2.writeExternal(newToolSettings);
        return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  public boolean isProperSetting(HighlightDisplayKey key) {
    if (myBaseProfile == null) {
      return false;
    }
    if (key == HighlightDisplayKey.UNUSED_SYMBOL && !myBaseProfile.getUnusedSymbolSettings().equals(getUnusedSymbolSettings())){
      return true;
    }
    final boolean toolsSettings = toolSettingsAreEqual(key, this, myBaseProfile);
    if (myDisplayLevelMap.keySet().contains(key)) {
      if (toolsSettings && myDisplayLevelMap.get(key).equals(myBaseProfile.getToolState(key))) {
        myDisplayLevelMap.remove(key);
        return false;
      }
      return true;
    }


    if (!toolsSettings) {
      myDisplayLevelMap.put(key, myBaseProfile.getToolState(key));
      return true;
    }

    return false;
  }



  public void setAdditionalJavadocTags(String tags){
    if (myBaseProfile != null && myBaseProfile.getAdditionalJavadocTags().length() > 0) {
      myAdditionalJavadocTags = tags.length() > myBaseProfile.getAdditionalJavadocTags().length()
                                ? tags.substring(myBaseProfile.getAdditionalJavadocTags().length() + 1).trim()
                                : "";
    }
    else {
      myAdditionalJavadocTags = tags;
    }
  }

  public void setAdditionalHtmlTags(String tags){
    if (myBaseProfile != null &&
        myBaseProfile.getAdditionalHtmlTags().length() > 0) {
      myAdditionalHtmlTags = tags.length() > myBaseProfile.getAdditionalHtmlTags().length()
                             ? tags.substring(myBaseProfile.getAdditionalHtmlTags().length() + 1).trim()
                             : "";
    }
    else {
      myAdditionalHtmlTags = tags;
    }
  }

  public void setAdditionalHtmlAttributes(String attributes){
    if (myBaseProfile != null && myBaseProfile.getAdditionalHtmlAttributes().length() > 0) {
      myAdditionalHtmlAttributes = attributes.length() > myBaseProfile.getAdditionalHtmlAttributes().length()
                                   ? attributes.substring(myBaseProfile.getAdditionalHtmlAttributes().length() + 1).trim()
                                   : "";
    }
    else {
      myAdditionalHtmlAttributes = attributes;
    }
  }

  public void setAdditionalNotRequiredHtmlAttributes(String attributes){
    if (myBaseProfile != null && myBaseProfile.getAdditionalNotRequiredHtmlAttributes().length() > 0) {
      myAdditionalRequiredHtmlAttributes = attributes.length() > myBaseProfile.getAdditionalNotRequiredHtmlAttributes().length()
                                           ? attributes.substring(myBaseProfile.getAdditionalNotRequiredHtmlAttributes().length() + 1).trim()
                                           : "";
    }
    else {
      myAdditionalRequiredHtmlAttributes = attributes;
    }
  }

  public void resetToBase() {
    myDisplayLevelMap.clear();
    myAdditionalHtmlAttributes = DEFAULT_ADDITIONAL_HTML_ATTRIBUTES;
    myAdditionalHtmlTags = DEFAULT_ADDITIONAL_HTML_TAGS;
    myAdditionalJavadocTags = "";
    myAdditionalRequiredHtmlAttributes = "";
    copyToolsConfigurations(myBaseProfile);
    myInitialized = true;
  }

  private void setDefaultErrorLevels() {
    myDisplayLevelMap.put(HighlightDisplayKey.UNUSED_IMPORT, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.UNUSED_SYMBOL, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.UNCHECKED_WARNING, new ToolState(HighlightDisplayLevel.WARNING));
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey) {
    return getToolState(inspectionToolKey).getLevel();
  }

  private ToolState getToolState(HighlightDisplayKey key) {
    ToolState state = myDisplayLevelMap.get(key);
    if (state == null) {
      if (myBaseProfile != null) {
        state = myBaseProfile.getToolState(key);
      }
    }
    //default level for converted profiles
    if (state == null) {
      state = new ToolState(HighlightDisplayLevel.WARNING, false);
    }
    return state;
  }

  private void readExternal(Element element) throws InvalidDataException {
    myDisplayLevelMap.clear();
    final String version = element.getAttributeValue(VERSION_TAG);
    if (version == null || !version.equals(VALID_VERSION)) {
      try {
        element = InspectionProfileConvertor.convertToNewFormat(myFile, this);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (JDOMException e) {
        LOG.error(e);
      }
    }
    for (final Object o : element.getChildren(INSPECTION_TOOL_TAG)) {
      Element toolElement = (Element)o;

      String toolClassName = toolElement.getAttributeValue(CLASS_TAG);

      final String levelName = toolElement.getAttributeValue(LEVEL_TAG);
      HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
        level = HighlightDisplayLevel.WARNING;
      }

      InspectionTool tool = myTools.get(toolClassName);
      if (tool != null) {
        tool.readExternal(toolElement);
      }

      HighlightDisplayKey key = HighlightDisplayKey.find(toolClassName);
      if (key == null) {
        if (tool instanceof LocalInspectionToolWrapper) {
          key = HighlightDisplayKey.register(toolClassName, tool.getDisplayName(), ((LocalInspectionToolWrapper)tool).getTool().getID());
        } else {
          key = HighlightDisplayKey.register(toolClassName);
        }
      }

      final String enabled = toolElement.getAttributeValue(ENABLED_TAG);
      myDisplayLevelMap.put(key, new ToolState(level, enabled != null && Boolean.parseBoolean(enabled)));
    }
    myVisibleTreeState.readExternal(element);

    final Element additionalJavadocs = element.getChild(ADDITIONAL_JAVADOC_TAGS_TAG);
    if (additionalJavadocs != null) {
      myAdditionalJavadocTags = additionalJavadocs.getAttributeValue(VALUE_TAG);
    }

    final Element additionalHtmlTags = element.getChild(ADDITIONAL_HTML_TAGS_TAG);
    if (additionalHtmlTags != null) {
      myAdditionalHtmlTags = additionalHtmlTags.getAttributeValue(VALUE_TAG);
    }

    final Element additionalHtmlAttributes = element.getChild(ADDITIONAL_HTML_ATTRIBUTES_TAG);
    if (additionalHtmlAttributes != null) {
      myAdditionalHtmlAttributes = additionalHtmlAttributes.getAttributeValue(VALUE_TAG);
    }

    final Element additionalRequiredHtmlAttributes = element.getChild(ADDITIONAL_REQUIRED_HTML_ATTRIBUTES_TAG);
    if (additionalRequiredHtmlAttributes != null) {
      myAdditionalRequiredHtmlAttributes = additionalRequiredHtmlAttributes.getAttributeValue(VALUE_TAG);
    }

    final Element unusedSymbolSettings = element.getChild(UNUSED_SYMBOL_SETTINGS_TAG);
    myUnusedSymbolSettings.readExternal(unusedSymbolSettings);

    myBaseProfile = InspectionProfileImpl.DEFAULT_PROFILE;
  }


  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(VERSION_TAG, VALID_VERSION);
    for (final HighlightDisplayKey key : myDisplayLevelMap.keySet()) {
      Element inspectionElement = new Element(INSPECTION_TOOL_TAG);
      final String toolName = key.toString();
      inspectionElement.setAttribute(CLASS_TAG, toolName);
      inspectionElement.setAttribute(LEVEL_TAG, getErrorLevel(key).toString());
      inspectionElement.setAttribute(ENABLED_TAG, Boolean.toString(isToolEnabled(key)));

      final InspectionTool tool = myTools.get(toolName);
      if (tool != null) {
        tool.writeExternal(inspectionElement);
      }
      element.addContent(inspectionElement);
    }
    myVisibleTreeState.writeExternal(element);

    if (myAdditionalJavadocTags != null && myAdditionalJavadocTags.length() != 0) {
      final Element additionalTags = new Element(ADDITIONAL_JAVADOC_TAGS_TAG);
      additionalTags.setAttribute(VALUE_TAG, myAdditionalJavadocTags);
      element.addContent(additionalTags);
    }

    if (myAdditionalHtmlTags != null && myAdditionalHtmlTags.length() != 0) {
      final Element additionalTags = new Element(ADDITIONAL_HTML_TAGS_TAG);
      additionalTags.setAttribute(VALUE_TAG, myAdditionalHtmlTags);
      element.addContent(additionalTags);
    }

    if (myAdditionalHtmlAttributes != null && myAdditionalHtmlAttributes.length() != 0) {
      final Element additionalAttributes = new Element(ADDITIONAL_HTML_ATTRIBUTES_TAG);
      additionalAttributes.setAttribute(VALUE_TAG, myAdditionalHtmlAttributes);
      element.addContent(additionalAttributes);
    }

    if (myAdditionalRequiredHtmlAttributes != null && myAdditionalRequiredHtmlAttributes.length() != 0) {
      final Element additionalAttributes = new Element(ADDITIONAL_REQUIRED_HTML_ATTRIBUTES_TAG);
      additionalAttributes.setAttribute(VALUE_TAG, myAdditionalRequiredHtmlAttributes);
      element.addContent(additionalAttributes);
    }

    final Element unusedSymbolSettings = new Element(UNUSED_SYMBOL_SETTINGS_TAG);
    myUnusedSymbolSettings.writeExternal(unusedSymbolSettings);
    element.addContent(unusedSymbolSettings);
  }

  public InspectionTool getInspectionTool(String shortName) {
    if (myTools.isEmpty()) {
      initInspectionTools();
    }
    return myTools.get(shortName);
  }

  public InspectionProfileManager getManager() {
    return myManager;
  }

  private static String getProfileName(File file) throws JDOMException, IOException {
    String name = getRootElementAttribute(file, PROFILE_NAME_TAG);
    if (name != null) return name;
    String fileName = file.getName();
    int extensionIndex = fileName.lastIndexOf(CONFIG_FILE_EXTENSION);
    return fileName.substring(0, extensionIndex);
  }

  private static String getRootElementAttribute(final File file, @NonNls String name) throws JDOMException, IOException {
    try {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      String profileName = root.getAttributeValue(name);
      if (profileName != null) return profileName;
    }
    catch (FileNotFoundException e) {
      //ignore
    }
    return null;
  }

  private static String getBaseProfileName(File file) throws JDOMException, IOException {
    return getRootElementAttribute(file, BASE_PROFILE_ATTR);
  }

  private void save(File file, String name) {
    try {
      Element root = new Element(ROOT_ELEMENT_TAG);
      root.setAttribute(PROFILE_NAME_TAG, name);
      writeExternal(root);
      if (file != null) {
        JDOMUtil.writeDocument(new Document(root), file, CodeStyleSettingsManager.getSettings(null).getLineSeparator());
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void load() {
    try {
      if (myFile != null) {
        Document document = JDOMUtil.loadDocument(myFile);
        readExternal(document.getRootElement());
        myInitialized = true;
      }
    }
    catch (FileNotFoundException e) {
      //ignore
    }
    catch (Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          Messages.showErrorDialog(InspectionsBundle.message("inspection.error.loading.message", myFile != null ? 0 : 1,  myFile != null ? myFile.getName() : ""), InspectionsBundle.message("inspection.errors.occured.dialog.title"));
        }
      }, ModalityState.NON_MMODAL);
    }
  }

  public UnusedSymbolSettings getUnusedSymbolSettings() {
    return myUnusedSymbolSettings;
  }

  public void setUnusedSymbolSettings(UnusedSymbolSettings settings) {
    myUnusedSymbolSettings = settings;
  }

  public boolean isDefault() {
    return myDisplayLevelMap.isEmpty();
  }

  public File getFile() {
    return myFile;
  }

  public InspectionTool[] getInspectionTools() {
    if (myTools.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
     initInspectionTools();
    }
    ArrayList<InspectionTool> result = new ArrayList<InspectionTool>();
    result.addAll(myTools.values());
    return result.toArray(new InspectionTool[result.size()]);
  }

  public void initInspectionTools() {
    if (myBaseProfile != null){
      myBaseProfile.initInspectionTools();
    }
    final InspectionTool[] tools = InspectionToolRegistrar.getInstance().createTools();
    for (InspectionTool tool : tools) {
      myTools.put(tool.getShortName(), tool);
    }
    if (mySource != null){
      copyToolsConfigurations(mySource);
    }
    load();
  }

  public LocalInspectionTool[] getHighlightingLocalInspectionTools() {
    ArrayList<LocalInspectionTool> enabled = new ArrayList<LocalInspectionTool>();
    final InspectionTool[] tools = getInspectionTools();
    for (InspectionTool tool : tools) {
      if (tool instanceof LocalInspectionToolWrapper) {
        final ToolState state = getToolState(HighlightDisplayKey.find(tool.getShortName()));
        if (state.isEnabled()) {
          enabled.add(((LocalInspectionToolWrapper)tool).getTool());
        }
      }
    }
    return enabled.toArray(new LocalInspectionTool[enabled.size()]);
  }

  public ModifiableModel getModifiableModel() {
    return new InspectionProfileImpl(this);
  }

  public String getAdditionalJavadocTags() {
    if (myBaseProfile != null) {
      return myBaseProfile.getAdditionalJavadocTags().length() > 0 ? myBaseProfile.getAdditionalJavadocTags() +
                                                                     (myAdditionalJavadocTags.length() > 0
                                                                      ? "," + myAdditionalJavadocTags
                                                                      : "") :
                                                                            myAdditionalJavadocTags;
    }
    return myAdditionalJavadocTags;
  }

  public String getAdditionalHtmlTags() {
    if (myBaseProfile != null) {
      return myBaseProfile.getAdditionalHtmlTags().length() > 0 ?
             myBaseProfile.getAdditionalHtmlTags() +
             (myAdditionalHtmlTags.length() > 0 ? "," + myAdditionalHtmlTags
              : "") :
                    myAdditionalHtmlTags;
    }
    return myAdditionalHtmlTags;
  }

  public String getAdditionalHtmlAttributes() {
    if (myBaseProfile != null) {
      return myBaseProfile.getAdditionalHtmlAttributes().length() > 0 ?
             myBaseProfile.getAdditionalHtmlAttributes() +
             (myAdditionalHtmlAttributes.length() > 0 ? "," + myAdditionalHtmlAttributes
              : "") :
                    myAdditionalHtmlAttributes;
    }
    return myAdditionalHtmlAttributes;
  }

  public String getAdditionalNotRequiredHtmlAttributes() {
    if (myBaseProfile != null) {
      return myBaseProfile.getAdditionalNotRequiredHtmlAttributes().length() > 0 ?
             myBaseProfile.getAdditionalNotRequiredHtmlAttributes() +
             (myAdditionalRequiredHtmlAttributes.length() > 0 ? "," + myAdditionalRequiredHtmlAttributes
              : "") :
                    myAdditionalRequiredHtmlAttributes;
    }
    return myAdditionalRequiredHtmlAttributes;
  }

  public void copyFrom(InspectionProfileImpl profile) {
    if (profile == null) return;
    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(profile.myDisplayLevelMap);
    myAdditionalJavadocTags = profile.myAdditionalJavadocTags;
    myAdditionalHtmlTags = profile.myAdditionalHtmlTags;
    myAdditionalHtmlAttributes = profile.myAdditionalHtmlAttributes;
    myAdditionalRequiredHtmlAttributes = profile.myAdditionalRequiredHtmlAttributes;
    myBaseProfile = profile.myBaseProfile;
    copyToolsConfigurations(profile);
  }

  public void inheritFrom(InspectionProfileImpl profile) {
    myBaseProfile = profile;
    copyToolsConfigurations(profile);
  }

  private void copyToolsConfigurations(InspectionProfileImpl profile) {
    myUnusedSymbolSettings = profile.myUnusedSymbolSettings.copySettings();
    try {
      if (!profile.myTools.isEmpty()) {
        final InspectionTool[] inspectionTools = getInspectionTools();
        for (InspectionTool inspectionTool : inspectionTools) {
          copyToolConfig(inspectionTool, profile);
        }
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private void copyToolConfig(final InspectionTool inspectionTool, final InspectionProfileImpl profile)
    throws WriteExternalException, InvalidDataException {
    final String name = inspectionTool.getShortName();
    final InspectionTool tool = profile.getInspectionTool(name);
    if (tool != null){
      @NonNls String tempRoot = "config";
      Element config = new Element(tempRoot);
      tool.writeExternal(config);
      inspectionTool.readExternal(config);
      addInspectionTool(inspectionTool);
    }
  }

  //make public for tests only
  public void addInspectionTool(InspectionTool inspectionTool){
    myTools.put(inspectionTool.getShortName(), inspectionTool);
  }

  public void cleanup() {
    if (myTools.isEmpty()) return;
    if (!myTools.isEmpty()) {
      for (final String key : myTools.keySet()) {
        final InspectionTool tool = myTools.get(key);
        if (tool.getManager() != null) {
          tool.cleanup();
        }
      }
    }
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void enableTool(String inspectionTool){
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), true));
  }

  public void disableTool(String inspectionTool){
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), false));
  }


  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    setState(key, new ToolState(level, isToolEnabled(key)));
  }

  private void setState(HighlightDisplayKey key, ToolState state) {
    if (myBaseProfile != null &&
        state.equals(myBaseProfile.getToolState(key))) {
      myDisplayLevelMap.remove(key);
    }
    else {
      myDisplayLevelMap.put(key, state);
    }
  }

  public boolean isToolEnabled(HighlightDisplayKey key) {
    final ToolState toolState = getToolState(key);
    if (toolState != null) {
      return toolState.isEnabled();
    }
    return false;
  }

  public boolean isExecutable() {
    if (myTools.isEmpty()){
      //initialize
      initInspectionTools();
    }
    for (String name : myTools.keySet()) {
      if (isToolEnabled(HighlightDisplayKey.find(name))){
        return true;
      }
    }
    return false;
  }

  //invoke when isChanged() == true
  public void commit() {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    mySource = null;
    myManager.initProfile(this);
  }

  private void commit(InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.myName;
    myDisplayLevelMap = inspectionProfile.myDisplayLevelMap;
    myVisibleTreeState = inspectionProfile.myVisibleTreeState;
    myBaseProfile = inspectionProfile.myBaseProfile;
    myTools = inspectionProfile.myTools;

    myAdditionalJavadocTags = inspectionProfile.myAdditionalJavadocTags;
    myAdditionalRequiredHtmlAttributes = inspectionProfile.myAdditionalRequiredHtmlAttributes;
    myAdditionalHtmlAttributes = inspectionProfile.myAdditionalHtmlAttributes;
    myAdditionalHtmlTags = inspectionProfile.myAdditionalHtmlTags;

    myUnusedSymbolSettings = inspectionProfile.myUnusedSymbolSettings.copySettings();
    myFile = inspectionProfile.myFile;
    save(myFile != null ? myFile : new File(InspectionProfileManager.getProfileDirectory(), myName + CONFIG_FILE_EXTENSION), myName);
  }

  private static class ToolState {
    private HighlightDisplayLevel myLevel;
    private boolean myEnabled;

    public ToolState(final HighlightDisplayLevel level, final boolean enabled) {
      myLevel = level;
      myEnabled = enabled;
    }

    public ToolState(final HighlightDisplayLevel level) {
      myLevel = level;
      myEnabled = true;
    }

    public HighlightDisplayLevel getLevel() {
      return myLevel;
    }

    public void setLevel(final HighlightDisplayLevel level) {
      myLevel = level;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }

    public boolean equals(Object object) {
      if (!(object instanceof ToolState)) return false;
      final ToolState state = (ToolState)object;
      return myLevel == state.getLevel() &&
             myEnabled == state.isEnabled();
    }

    public int hashCode() {
      return myLevel.hashCode();
    }
  }

}
