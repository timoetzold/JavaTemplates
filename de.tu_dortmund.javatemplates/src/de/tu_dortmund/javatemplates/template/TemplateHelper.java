package de.tu_dortmund.javatemplates.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.stringtemplate.v4.ST;

import de.tu_dortmund.javatemplates.types.ITypeVisitor;
import de.tu_dortmund.javatemplates.types.Type;
import de.tu_dortmund.javatemplates.types.TypeFinder;
import de.tu_dortmund.javatemplates.types.TypeVisitor;
import de.tu_dortmund.javatemplates.types.other.Args;
import de.tu_dortmund.javatemplates.types.other.ArgsDef;
import de.tu_dortmund.javatemplates.types.other.Expr;
import de.tu_dortmund.javatemplates.types.other.Name;
import de.tu_dortmund.javatemplates.types.other.ReturnType;

/**
 * Implementation of helping methods used by {@link de.tu_dortmund.javatemplates.plugin.CompilationParticipant} and {@link Template}.
 * 
 * @author Timo Etzold
 *
 */
public class TemplateHelper implements ITemplateHelper {
  private TypeFinder typefinder = new TypeFinder();
  private ITypeVisitor visitor = new TypeVisitor();
  
  /**
   * Returns the {@link TypeFinder} used by this TemplateHelper.
   * 
   * @return the {@link TypeFinder}
   */
  public TypeFinder getTypefinder() {
    return typefinder;
  }

  /**
   * Sets the {@link TypeFinder} used by this TemplateHelper.
   * 
   * @param typefinder the {@link TypeFinder} to be used
   */
  public void setTypefinder(TypeFinder typefinder) {
    this.typefinder = typefinder;
  }

  /**
   * Returns the {@link ITypeVisitor} used by this TemplateHelper.
   * 
   * @return the  {@link ITypeVisitor}
   */
  public ITypeVisitor getVisitor() {
    return visitor;
  }

  /**
   * Sets the {@link ITypeVisitor} used by this TemplateHelper.
   * 
   * @param visitor the {@link ITypeVisitor} to be used
   */
  public void setVisitor(ITypeVisitor visitor) {
    this.visitor = visitor;
  }

  /**
   * Fixes errors in the source code which occur because of the usage of template variables. <br>
   * The method creates an instance of ITemplate for the source code with the {@link TemplateFactory} <br>
   * and uses {@link Pattern} to fill it with replacement Strings <br>
   * generated by {@link #getReplacementForTemplateVariable(ITemplate, TemplateVariable) getReplacementForTemplateVariable}. <br>
   * 
   * @param sSource the source code of the template (including template variables)
   * @return the source code of the template containing no errors created by template variables
   */
  public String fixErrors(String sSource){
    ITemplate template = TemplateFactory.createTemplateForSource(sSource);
    
    Matcher matcher = Pattern.compile(template.getRegex()).matcher(sSource);
    StringBuffer sb = new StringBuffer();
    while(matcher.find()){
      TemplateVariable var = TemplateHelper.getTemplateVariableByName(template.getTemplateVariables(), matcher.group("name"));
      matcher.appendReplacement(sb, getReplacementForTemplateVariable(template, var));
    }
    matcher.appendTail(sb);
    
    return sb.toString();
  }
  
  /**
   * Returns the replacement String for a given {@link TemplateVariable} in an {@link ITemplate}. <br>
   * First the exact {@link Type} is determined by {@link #getVarType(String, TemplateVariable) getVarType}, <br>
   * then the {@link Type} will be visited by the {@link TypeVisitor} to get the replacement.
   * 
   * @param template the template which contains the {@link TemplateVariable}
   * @param templateVariable the template variable inside the {@link ITemplate}
   * @return the replacement String for the template variable in the template
   */
  public String getReplacementForTemplateVariable(ITemplate template, TemplateVariable templateVariable){
    String sLine = getTemplateLine(template, templateVariable);
    
    Type type = getVarType(sLine, templateVariable);
    if(type != null){
      return type.accept(visitor);
    }
    return "";
  }
  
  /**
   * Determines the {@link Type} of the {@link TemplateVariable}. <br>
   * All except VALUE can be determined by {@link TemplateVariable.TYPE}. <br>
   * The exact {@link Type} of a VALUE is computed by {@link #getValueType(String) getValueType}.
   * 
   * @param sLine the complete line which contains the {@link TemplateVariable}
   * @param templateVariable the {@link TemplateVariable} whose {@link Type} is to be determined
   * @return the exact {@link Type} of the {@link TemplateVariable}
   */
  protected Type getVarType(String sLine, TemplateVariable templateVariable){
    switch(templateVariable.getType()){
    case ARGS:
      return new Args();
    case ARGSDEF:
      return new ArgsDef();
    case EXPR:
      return new Expr();
    case NAME:
      return new Name();
    case RETURNTYPE:
      return new ReturnType();
    case VALUE:
      return getValueType(sLine);
    default:
      return null;
    }
  }
  
  /**
   * Returns the exact {@link Type} of a {@link TemplateVariable} whose {@link TemplateVariable.TYPE} is VALUE. <br>
   * The line which contains the {@link TemplateVariable} is parsed by eclipse's {@link ASTParser} into an {@link ASTNode}
   * which is visited by the {@link TypeFinder}.
   * 
   * @param sLine the line containing the {@link TemplateVariable} whose VALUE {@link Type} is to be determined
   * @return the exact {@link Type} of the {@link TemplateVariable}
   */
  protected Type getValueType(String sLine){
    typefinder.reset();
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sLine.toCharArray());
    parser.setKind(ASTParser.K_STATEMENTS);
    parser.setStatementsRecovery(true);
 
    ASTNode node = parser.createAST(null);
    node.accept(typefinder);
    
    return typefinder.getType();
  }
  
  /**
   * Determines the line in which a {@link TemplateVariable} occurs first.
   * 
   * @param template the {@link ITemplate} containing the {@link TemplateVariable}
   * @param templateVariable the {@link TemplateVariable} whose first line of occurrence is to be determined
   * @return the first line in which the {@link TemplateVariable} occurs
   */
  protected String getTemplateLine(ITemplate template, TemplateVariable templateVariable){
    if(templateVariable == null || template == null){
      throw new IllegalArgumentException("TemplateVariable or Template is null.");
    }
    String sSource = template.getCode();
    int iStart = 0;
    int iEnd = sSource.indexOf("\n", getMatcherEndPosOfTemplateVariable(template, templateVariable));
    String sShortSource = sSource.substring(0, iEnd);
    Pattern pattern = Pattern.compile("\n");
    Matcher matcher = pattern.matcher(sShortSource);
    while(matcher.find()){
      iStart = matcher.end();
    }
    return sSource.substring(iStart, iEnd);
  }
  
  /**
   * Determines the end of the line in which a {@link TemplateVariable} occurs first.
   * 
   * @param template the {@link ITemplate} containing the {@link TemplateVariable}
   * @param templateVariable the {@link TemplateVariable} whose end of line of the first occurrence is to be determined
   * @return the index of the end of line in which the {@link TemplateVariable} occurs first
   */
  private int getMatcherEndPosOfTemplateVariable(ITemplate template, TemplateVariable templateVariable){
    Matcher matcher = Pattern.compile(template.getRegex()).matcher(template.getCode());
    
    while(matcher.find()){
      if(StringUtils.equals(templateVariable.getName(), matcher.group("name"))){
        return matcher.end();
      }
    }
    
    return 0;
  }
  
  /**
   * Inserts the code from the {@link Map} into each {@link TemplateVariable} of the {@link ITemplate} <br>
   * and parses the resulting Code into a {@link CompilationUnit}. <br>
   * <br>
   * {@link IllegalArgumentException} will be thrown if: <br>
   * - a {@link TemplateVariable} of this template has no name or type<br>
   * - the {@link Map} is missing code for a variable<br>
   * - the type of the code which is to be inserted into a variable does not match the variable's type<br>
   * - no type can be determined for the code, which happens if there are errors in the code.
   * 
   * @param template the template to be filled
   * @param attributes a map with content for each template variable
   * @return the compiled CompilationUnit
   * @throws IllegalArgumentException if<br> 
   * - a {@link TemplateVariable} has errors or <br>
   * - the {@link Map} has errors
   */
  public CompilationUnit fillTemplate(ITemplate template, Map<String, String> attributes) throws IllegalArgumentException{
    checkAttributesAndVariables(template.getTemplateVariables(), attributes);
    ST stTemplate = new ST(getStringTemplateCompatibleCode(template));
    
    for(Map.Entry<String,String> mapEntry : attributes.entrySet()){
      stTemplate.add(mapEntry.getKey(), mapEntry.getValue());
    }
    
    String generatedSource = stTemplate.render();
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(generatedSource.toCharArray());
    parser.setCompilerOptions(options);
    CompilationUnit result = (CompilationUnit) parser.createAST(null);
    
    return result;
  }
  
  /**
   * Changes the source code of an {@link ITemplate} so that {@link ST StringTemplate} can use it as a template.
   * 
   * @param template the template whose source code is to be made compatible with {@link ST StringTemplate}
   * @return the source code which {@link ST StringTemplate} can use as a template
   */
  protected String getStringTemplateCompatibleCode(ITemplate template){
    String sCode = template.getCode();

    //escape < to prevent generics from starting an attribute in the template
    sCode = Pattern.compile("<").matcher(sCode).replaceAll(Matcher.quoteReplacement("\\<"));
    
    //convert Template delimiters to StringTemplate delimiters and remove TYPE
    Matcher matcher = Pattern.compile(template.getRegex()).matcher(sCode);
    StringBuffer sb = new StringBuffer();
    while(matcher.find()){
      matcher.appendReplacement(sb, "<" + matcher.group("name") + ">");
    }
    matcher.appendTail(sb);
    
    return sb.toString();
  }
  
  /**
   * Checks the List of {@link TemplateVariable}s and the Map containing content to be inserted into the variables. <br>
   * <br>
   * {@link IllegalArgumentException} will be thrown if: <br>
   * - a {@link TemplateVariable} of this template has no name or type<br>
   * - the {@link Map} is missing code for a variable<br>
   * - the type of the code which is to be inserted into a variable does not match the variable's type<br>
   * - no type can be determined for the code, which happens if there are errors in the code.
   * 
   * @param variables the list of {@link TemplateVariable}s to be checked
   * @param attributes the corresponding Map to be checked
   * @throws IllegalArgumentException if the List or the Map contains errors
   */
  protected void checkAttributesAndVariables(List<TemplateVariable> variables, Map<String, String> attributes) throws IllegalArgumentException{
    for(TemplateVariable variable : variables){
      if(variable.getName() == null){
        throw new IllegalArgumentException("A variable has no name.");
      }
      if(!attributes.containsKey(variable.getName())){
        throw new IllegalArgumentException("Attributes missing variable: " + variable.getName());
      }
      if(variable.getType() == null){
        throw new IllegalArgumentException("Variable " + variable.getName() + " is untyped.");
      }
      List<TemplateVariable.TYPE> possibleAttributeTypes = getTypesForAttribute(attributes.get(variable.getName()));
      if(possibleAttributeTypes.isEmpty()){
        throw new IllegalArgumentException("No possible Type for attribute found. It may consist of errors.");
      }
      if(!possibleAttributeTypes.contains(variable.getType())){
        throw new IllegalArgumentException("Attribute has possible types " + possibleAttributeTypes.toString() + " but " + variable.getType() + " was expected.");
      }
    }
  }
  
  /**
   * Determines a list of possible {@link TemplateVariable.TYPE}s for a String which is to be inserted into a {@link TemplateVariable}.
   * 
   * @param sAttribute the code whose type is to be determined
   * @return a list of possible {@link TemplateVariable.TYPE}s
   */
  protected List<TemplateVariable.TYPE> getTypesForAttribute(String sAttribute){
    List<TemplateVariable.TYPE> list = new ArrayList<>();
    
    sAttribute = StringUtils.trim(sAttribute);
    
    if(StringUtils.isBlank(sAttribute)){
      list.add(TemplateVariable.TYPE.EXPR);
      list.add(TemplateVariable.TYPE.ARGS);
      list.add(TemplateVariable.TYPE.ARGSDEF);
      return list;
    }
    
    if(isEXPR(sAttribute)){
      list.add(TemplateVariable.TYPE.EXPR);
    }
    else{
      if(isARGS(sAttribute)){
        list.add(TemplateVariable.TYPE.ARGS);
      }
      
      if(isARGSDEF(sAttribute)){
        list.add(TemplateVariable.TYPE.ARGSDEF);
      }    
      
      if(isNAME(sAttribute)){
        list.add(TemplateVariable.TYPE.NAME);
      }
      
      if(isRETURNTYPE(sAttribute)){
        list.add(TemplateVariable.TYPE.RETURNTYPE);
      }
  
      if(isVALUE(sAttribute)){
        list.add(TemplateVariable.TYPE.VALUE);
      }
    }
    
    return list;
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.ARGS
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.ARGS
   */
  protected boolean isARGS(String sAttribute){
    String sSource = "class TYPE{\npublic Type(){\n}\n\npublic void test(){\nSomeClass.someMethod(" + sAttribute + ");\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);   
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.ARGSDEF
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.ARGSDEF
   */
  protected boolean isARGSDEF(String sAttribute){
    String sSource = "class Type{\npublic Type(){\n}\n\npublic void test(" + sAttribute + "){\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);   
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.EXPR
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.EXPR
   */
  protected boolean isEXPR(String sAttribute){ 
    String sSource = "class Type{\npublic Type(){\n" + sAttribute + "\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.NAME
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.NAME
   */
  protected boolean isNAME(String sAttribute){
    String sSource = "class " + sAttribute + "{\npublic " + sAttribute + "(){\n}\n\npublic void test(){\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);   
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.RETURNTYPE
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.RETURNTYPE
   */
  protected boolean isRETURNTYPE(String sAttribute){   
    String sSource = "class Type{\npublic Type(){\n}\npublic " + sAttribute + " someMethod(){\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);   
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Checks if the String which is to be inserted into a {@link TemplateVariable} has the {@link TemplateVariable.TYPE}.VALUE
   * 
   * @param sAttribute the String to be analyzed
   * @return true if the String can be used as {@link TemplateVariable.TYPE}.VALUE
   */
  protected boolean isVALUE(String sAttribute){ 
    String sSource = "class Type{\npublic Type(){\nObject o = " + sAttribute + ";\n}\n}";
    
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
    
    ASTParser parser = ASTParser.newParser(AST.JLS8);
    parser.setSource(sSource.toCharArray());
    parser.setCompilerOptions(options);   
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
 
    CompilationUnit cu = (CompilationUnit) parser.createAST(null);
    
    return (cu != null && cu.getProblems().length == 0);
  }
  
  /**
   * Returns the {@link TemplateVariable} in the list with the given name or {@code null}. <br>
   * If any parameter is {@code null} this method returns {@code null}. <br>
   * If the list contains an element with the given name it is returned, else {@code null} is returned.
   *  
   * @param variables a list of {@link TemplateVariable}s
   * @param sName the name of the searched {@link TemplateVariable}
   * @return the {@link TemplateVariable} if it can be found, else {@code null}
   */
  public static TemplateVariable getTemplateVariableByName(List<TemplateVariable> variables, String sName){
    if(variables == null || sName == null){
      return null;
    }
    for(TemplateVariable variable : variables){
      if(variable.getName() != null && variable.getName().equals(sName)){
        return variable;
      }
    }
    return null;
  }
}