package cfscript.parser;

import cfscript.object.parser.ObjectCustomListener;
import cfscript.object.parser.ObjectLexer;
import cfscript.object.parser.ObjectParser;
import cfscript.typewriter.SymbolTable;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class CfscriptSourceListener extends CfscriptBaseListener {

    private final TokenStreamRewriter rewriter;
    private final String packageName;
    private final SymbolTable symbolTable;
    private String componentName = "";
    private String context = "";
    private String filepath = "";
    private final StringBuilder translation = new StringBuilder();
    private final ArrayList<String> imports = new ArrayList<>();

    public StringBuilder getTranslation() {
        return translation;
    }

    public CfscriptSourceListener(TokenStreamRewriter rewriter, CommonTokenStream tokens, String filePath, String finalPackageName, SymbolTable symbolTable) {
        super();
        this.rewriter = rewriter;
        this.filepath = filePath;
        this.packageName = finalPackageName;
        this.symbolTable = symbolTable;
        // Imports common to all source files
        addImportIfNotFound(imports, "import java.util.*;");
        addImportIfNotFound(imports, "import java.lang.*;");
        addImportIfNotFound(imports, "import io.quarkus.qute.*;"); // Qute Template Library
        addImportIfNotFound(imports, "import io.quarkus.mailer.*;");
        addImportIfNotFound(imports, "import io.quarkus.logging.Log;"); // Simplified Logging
        addImportIfNotFound(imports, "import io.quarkus.runtime.*;"); // startup event
        addImportIfNotFound(imports, "import static cfscript.library.StdLib.*;"); // Cfscript STD functions like len and isNull.
        addImportIfNotFound(imports, "import java.io.File;");
        addImportIfNotFound(imports, "import jakarta.persistence.*;");
        addImportIfNotFound(imports, "import jakarta.enterprise.event.Observes;");
        addImportIfNotFound(imports, "import io.quarkus.elytron.security.common.BcryptUtil;");

    }

    @Override
    public void enterVariableStatement(CfscriptParser.VariableStatementContext ctx) {
        //out.println(ctx.getText());
    }

    @Override
    public void enterNonVarVariableStatement(CfscriptParser.NonVarVariableStatementContext ctx) {
        if (!ctx.variableName().getText().startsWith("this.") && !ctx.variableName().getText().contains(".") &&
                symbolTable.get(ctx.variableName().getText()) != null &&
                symbolTable.get(ctx.variableName().getText()).getUseVar()) {
            rewriter.insertBefore(ctx.start, "var ");
        } else if (!ctx.variableName().getText().startsWith("this.") && !ctx.variableName().getText().contains(".") &&
                symbolTable.get(ctx.variableName().getText()) == null) {
            rewriter.insertBefore(ctx.start, "var ");
        }

    }

    @Override
    public void enterComponentDefinition(CfscriptParser.ComponentDefinitionContext ctx) {
        this.context = "component";

        // Iterate the attributes and find the component.
        findComponentName(ctx);
        String extendClass = findExtends(ctx);
        var newComponentText = "public class " + this.componentName + " ";

        // Translate CFScript component to Java class
        for (var importStm : ctx.imports()) {
           System.out.println("Import Stm: " + importStm.getText());
           addImportIfNotFound(imports, importStm.getText().replaceFirst("import", "import "));
        }

        var annotation = "";
        for (var id : ctx.annotation()) {
            annotation = id.getText();
            // handle imports.
            if (annotation.startsWith("@Path")) {
                addImportIfNotFound(imports, "import jakarta.ws.rs.*;");
                addImportIfNotFound(imports, "import jakarta.ws.rs.core.*;");
                addImportIfNotFound(imports, "import jakarta.validation.Valid;");
                addImportIfNotFound(imports, "import jakarta.annotation.security.*;");
                addImportIfNotFound(imports, "import java.net.URI;");
                addImportIfNotFound(imports, "import java.net.URI;");
                addImportIfNotFound(imports, "import io.quarkus.panache.common.*;");
                addImportIfNotFound(imports, "import org.eclipse.microprofile.jwt.JsonWebToken;");

            }else if (annotation.startsWith("@Entity")) {
                addImportIfNotFound(imports, "import jakarta.persistence.*;");
                addImportIfNotFound(imports, "import jakarta.validation.constraints.*;");
                addImportIfNotFound(imports, "import io.quarkus.hibernate.orm.panache.*;");
                addImportIfNotFound(imports, "import io.quarkus.hibernate.orm.panache.common.*;");
                addImportIfNotFound(imports, "import io.quarkus.security.jpa.*;");
                addImportIfNotFound(imports, "import com.fasterxml.jackson.annotation.*;");
                addImportIfNotFound(imports,
                        "import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;");
                addImportIfNotFound(imports, "import org.hibernate.search.engine.backend.types.Sortable;");
            }else if (annotation.startsWith("@RunOnVirtualThread")) {
                addImportIfNotFound(imports, "import io.smallrye.common.annotation.RunOnVirtualThread;");
            }else if (annotation.startsWith("@NonBlocking")) {
                addImportIfNotFound(imports, "import io.smallrye.common.annotation.NonBlocking;");
            }else if (annotation.startsWith("@Transactional")) {
                addImportIfNotFound(imports, "import jakarta.transaction.Transactional;");
            }else if (annotation.startsWith("@Singleton")) {
                addImportIfNotFound(imports, "import jakarta.inject.Singleton;");
            }else if (annotation.startsWith("@ApplicationScoped") || annotation.startsWith("@RequestScoped")) {
                addImportIfNotFound(imports, "import jakarta.enterprise.context.*;");
            }

            newComponentText = annotation + "\n" + newComponentText;
        }

        if (extendClass != null) {
            newComponentText = newComponentText + " extends " + extendClass;
        }
        rewriter.replace(ctx.start, ctx.stop, newComponentText);
    }

    private void findComponentName(CfscriptParser.ComponentDefinitionContext ctx) {
        this.componentName = ctx.keyValue().stream()
                .filter(kv -> kv.Identifier().getText().equals("name"))
                .map(kv -> kv.StringLiteral().getText().replaceAll("\"", ""))  // Strip quotes
                .findFirst()
                .orElse(this.getFileName(this.filepath));
    }

    private String findExtends(CfscriptParser.ComponentDefinitionContext ctx) {
        return ctx.keyValue().stream()
                .filter(kv -> kv.Identifier().getText().equals("extends"))
                .map(kv -> kv.StringLiteral().getText().replaceAll("\"", ""))  // Strip quotes
                .findFirst()
                .orElse(null);
    }

    public String getFileName(String filepath) {
        // Convert the file path string to a Path object
        Path path = Paths.get(filepath);

        // Get the file name from the Path object
        Path fileNamePath = path.getFileName();

        // If the path ends with a directory separator, the file name will be null
        if (fileNamePath == null) {
            return "default";  // Or whatever default value you want to use
        }

        // Convert the file name Path object to a string
        return getFilenameWithoutExtension(fileNamePath);
    }

    private static String getFilenameWithoutExtension(Path fileNamePath) {
        String fileNameWithExtension = fileNamePath.toString();

        // Get the index of the last dot '.' in the file name
        int lastDotIndex = fileNameWithExtension.lastIndexOf('.');

        // Check if there is a dot in the file name and extract the part before it
        String fileNameWithoutExtension;
        if (lastDotIndex != -1) {
            fileNameWithoutExtension = fileNameWithExtension.substring(0, lastDotIndex);
        } else {
            // If there's no dot in the file name, use the entire file name
            fileNameWithoutExtension = fileNameWithExtension;
        }
        return fileNameWithoutExtension;
    }

    @Override
    public void enterArgumentDefinition(CfscriptParser.ArgumentDefinitionContext ctx) {
        String arg = "";
        var typeIdentifier = ctx.Identifier(0);
        //System.out.println(typeIdentifier.getText());
        
        if (typeIdentifier.getText().equalsIgnoreCase("array")) {
            arg = " ArrayList<Object> ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("struct")) {
            arg = " HashMap<String, Object> ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("integer")) {
            arg = " Integer ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("double")) {
            arg = " Double ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("long")) {
            arg = " Long ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("boolean")) {
            arg = " Boolean ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("string")) {
            arg = " String ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("numeric")) {
            arg = " Double ";
        }
        else if (typeIdentifier.getText().equalsIgnoreCase("any")) {
            arg = " Object ";
        }else{
            arg = " %s ".formatted(typeIdentifier.getText()); // default to whatever type they passed in
        }
        var newArg = ctx.getText().replace(typeIdentifier.getText(), arg);
        rewriter.replace(ctx.start, ctx.stop, newArg);
    }

    @Override
    public void enterPropertyDeclaration(CfscriptParser.PropertyDeclarationContext ctx) {
        //println("Enter Property");
        this.context = "property";
        // Translate CFScript property to Java class
        String propertyName = ctx.keyValue().stream()
                .filter(kv -> kv.Identifier().getText().equals("name"))
                .map(kv -> kv.StringLiteral().getText().replaceAll("\"", ""))  // Strip quotes
                .findFirst()
                .orElse("defaultName");
        String propertyValue = ctx.keyValue().stream()
                .filter(kv -> kv.Identifier().getText().equals("default"))
                .map(kv -> kv.StringLiteral().getText())
                .findFirst()
                .orElse(null);

        StringBuilder property = new StringBuilder();
        var symbol = symbolTable.getSymbol(propertyName);

        // Handle declared first.
        // Strip the quotes around the value. Add them back in for field string type.
        if (propertyValue != null) {
            if (propertyValue.startsWith("\"") && propertyValue.endsWith("\"") || propertyValue.startsWith("'")
                    && propertyValue.endsWith("'")) {
                propertyValue = propertyValue.substring(1, propertyValue.length() - 1);  // Remove quotes
            }
        }
        if (symbol.getDeclaredType() != null) {
            if (symbol.getDeclaredType().equalsIgnoreCase("array")) {
                property = new StringBuilder("ArrayList<Object> " + propertyName + " = new ArrayList<Object>();");
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("struct")) {
                property = new StringBuilder("HashMap<String, Object> " + propertyName + " = new HashMap<>();");
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("mailer")) {
                property = new StringBuilder("Mailer " + propertyName + ";");
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("entitymanager")) {
                property = new StringBuilder("EntityManager " + propertyName + ";");
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("jsonwebtoken")) {
                property = new StringBuilder("JsonWebToken " + propertyName + ";");
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("numeric") &&
                    symbol.getInferredType() != null) {
                property = new StringBuilder(symbol.getInferredType() + " " + propertyName + (propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("integer")) {
                property = new StringBuilder("Integer %s%s".formatted(propertyName, propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("double")) {
                property = new StringBuilder("Double %s%s".formatted(propertyName, propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("float")) {
                property = new StringBuilder("Float %s%s".formatted(propertyName, propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("boolean")) {
                property = new StringBuilder("Boolean %s%s".formatted(propertyName, propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("string")) {
                property = new StringBuilder("String %s%s".formatted(propertyName, propertyValue != null ? "=\"" + propertyValue + "\";" : ";"));
            }
            else if (symbol.getDeclaredType().equalsIgnoreCase("uuid")) {
                property = new StringBuilder("UUID %s%s".formatted(propertyName, propertyValue != null ? "=\"" + propertyValue + "\";" : ";"));
            }
            else{
                // use the declared type directly.
                property = new StringBuilder(symbol.getDeclaredType() + " " + propertyName + ";");
            }
        }else if (symbol.getInferredType() != null) {
            if (symbol.getInferredType().equalsIgnoreCase("integer") || symbol.getInferredType().equalsIgnoreCase("double")) {
                property = new StringBuilder(symbol.getInferredType() + " " + propertyName + (propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getInferredType().equalsIgnoreCase("boolean")) {
                property = new StringBuilder("Boolean " + propertyName + (propertyValue != null ? "=" + propertyValue + ";" : ";"));
            }
            else if (symbol.getInferredType().equalsIgnoreCase("string")) {
                property = new StringBuilder("String " + propertyName + (propertyValue != null ? "=\"" + propertyValue + "\";" : ";"));
            }
            else if (symbol.getInferredType().equalsIgnoreCase("array")) {
                property = new StringBuilder("ArrayList<Object> " + propertyName + ";");
            }
            else if (symbol.getInferredType().equalsIgnoreCase("struct")) {
                property = new StringBuilder("HashMap<String, Object> " + propertyName + ";");
            }
            else if (symbol.getInferredType().equalsIgnoreCase("uuid")) {
                property = new StringBuilder("UUID " + propertyName + ";");
            }
        }

        // Add the scope.
        if (symbolTable.getSymbol(this.componentName).getEntityComponent()) {
            property = new StringBuilder("public " + property);
        }

        // Check property annotations
        var annotation = "";
        for (var id : ctx.annotation()) {
            annotation = id.getText();
            property.insert(0, annotation + "\n");

            // Add imports
            if (annotation.startsWith("@Inject")) {
                addImportIfNotFound(imports, "import jakarta.inject.Inject;");
            }else if(annotation.startsWith("@ConfigProperty")) {
                addImportIfNotFound(imports, "import org.eclipse.microprofile.config.inject.ConfigProperty;");
            }
        }
        rewriter.replace(ctx.start, ctx.stop, property.toString());
    }


    @Override
    public void exitComponent(CfscriptParser.ComponentContext ctx) {
        // Add Package
        addImportIfNotFound(imports, "package "+ this.packageName +";\n");

        if (!imports.isEmpty()) {
            for (String anImport : imports) {
                rewriter.insertBefore(ctx.start, anImport + "\n");
            }
        }

    }
    @Override
    public void exitExpression(CfscriptParser.ExpressionContext ctx) {
        if (ctx.STRING_CONCAT_CHAR() != null) {
            Token token = ctx.STRING_CONCAT_CHAR().getSymbol();
            rewriter.replace(token, "+");
        }
    }

    @Override
    public void enterFunctionDeclaration(CfscriptParser.FunctionDeclarationContext ctx) {
        var annotation = "";
        // Translate CFScript component to Java class
        for (var id : ctx.annotation()) {
            // if the return value
            annotation = id.getText();
            // handle imports.
            if (annotation.startsWith("@RunOnVirtualThread")) {
                addImportIfNotFound(imports, "import io.smallrye.common.annotation.RunOnVirtualThread;");
            }else if (annotation.startsWith("@NonBlocking")) {
                addImportIfNotFound(imports, "import io.smallrye.common.annotation.NonBlocking;");
            }else if (annotation.startsWith("@Transactional")) {
                addImportIfNotFound(imports, "import jakarta.transaction.Transactional;");
            }
        }
    }

    @Override
    public void enterFunctionDefinition(CfscriptParser.FunctionDefinitionContext ctx) {
        //println("Entered FunctionDeclaration");
        this.context = "function";
        // TODO: Check if this is a constructor and handle
        var functionName = ctx.functionName().getText();
        System.out.println("Entered FunctionDeclaration: " + functionName);
        var functionScope=(symbolTable.get(functionName).getScope().name().equals("Public") ? "public" : "");
        var functionReturn="void";
        var isConstructor = false;

        for (var id : ctx.Identifier()) {
            if (id.toString().equalsIgnoreCase("public") ||
                    id.toString().equalsIgnoreCase("private") ||
                    id.toString().equalsIgnoreCase("remote")) {
                functionScope = ""; // Make everything package scope for Quarkus except resource methods.
                if (symbolTable.getSymbol(this.componentName).getRestComponent() ||
                        symbolTable.getSymbol(this.componentName).getEntityComponent()){
                    functionScope = "public";
                }
            }else {
                // if the return value
                functionReturn = id.getText();
                if (functionReturn.equalsIgnoreCase("response")) {
                    addImportIfNotFound(imports, "import jakarta.ws.rs.core.Response;");
                    functionReturn = "Response";
                }else if(functionReturn.equalsIgnoreCase("struct")){
                    functionReturn = "HashMap<String, Object>";
                }else if(functionReturn.equalsIgnoreCase("array")){
                    functionReturn = "ArrayList<Object>";
                }else if(functionReturn.equalsIgnoreCase("string")){
                    functionReturn = "String";
                }
            }
        }
        // If this is a constructor, then name it as one.
        if (functionName.equalsIgnoreCase("init")) {
            // use the constructor name instead of the method name
            isConstructor = true;
            functionName = this.componentName; // Use the defined name or the filename depending
            functionScope = "public";
        }

        var newFunction = ((!functionScope.isEmpty()) ? functionScope + " " : "") +
                (!isConstructor ? functionReturn + " " : "") + functionName;
        rewriter.replace(ctx.start, ctx.stop, newFunction);
    }

    @Override
    public void enterObjectLiteral(CfscriptParser.ObjectLiteralContext ctx) {
        parseObject(ctx.getText(), ctx);
    }

    @Override
    public void enterArrayLiteral(CfscriptParser.ArrayLiteralContext ctx) {
        parseObject(ctx.getText(), ctx);
    }

    @Override
    public void exitCollectionAccess(CfscriptParser.CollectionAccessContext ctx) {
        var newAccess = ctx.getText().replace("[", "").replace("]", "");
        newAccess = ".get(" + newAccess + ")";
        rewriter.replace(ctx.start, ctx.stop, newAccess);
    }

    private void parseObject(String text, ParserRuleContext ctx) {
        ObjectLexer lexer = new ObjectLexer(CharStreams.fromString(text));
        ObjectParser parser = new ObjectParser(new CommonTokenStream(lexer));
        ParseTree tree = parser.prog();
        ObjectCustomListener listener = new ObjectCustomListener();
        ParseTreeWalker.DEFAULT.walk(listener, tree);
        String javaCode = listener.getResult();
        rewriter.replace(ctx.start, ctx.stop, javaCode);
    }

    private void addImportIfNotFound(ArrayList<String> imports, String s) {
        if (!imports.contains(s)) {
            imports.add(s);
        }
    }
}

