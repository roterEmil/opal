/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.fixtures.string_analysis;

import org.opalj.fpcf.properties.string_analysis.StringDefinitions;
import org.opalj.fpcf.properties.string_analysis.StringDefinitionsCollection;

import static org.opalj.fpcf.properties.string_analysis.StringConstancyLevel.CONSTANT;

/**
 * This file contains various tests for the InterproceduralStringAnalysis. For further information
 * on what to consider, please see {@link LocalTestMethods}
 *
 * @author Patrick Mell
 */
public class InterproceduralTestMethods {

    public static final String JAVA_LANG = "java.lang";

    /**
     * {@see LocalTestMethods#analyzeString}
     */
    public void analyzeString(String s) {
    }

    @StringDefinitionsCollection(
            value = "a case where a very simple non-virtual function call is interpreted",
            stringDefinitions = {
                    @StringDefinitions(
                            expectedLevel = CONSTANT, expectedStrings = "java.lang.StringBuilder"
                    )
            })
    public void simpleNonVirtualFunctionCallTest() {
        String className = getStringBuilderClassName();
        analyzeString(className);
    }

    @StringDefinitionsCollection(
            value = "a case where the initialization of a StringBuilder depends on > 1 non-virtual "
                    + "function calls and a constant",
            stringDefinitions = {
                    @StringDefinitions(
                            expectedLevel = CONSTANT,
                            expectedStrings = "(java.lang.Runtime|java.lang.StringBuilder|ERROR)"
                    )
            })
    public void initFromNonVirtualFunctionCallTest(int i) {
        String s;
        if (i == 0) {
            s = getRuntimeClassName();
        } else if (i == 1) {
            s = getStringBuilderClassName();
        } else {
            s = "ERROR";
        }
        StringBuilder sb = new StringBuilder(s);
        analyzeString(sb.toString());
    }

    @StringDefinitionsCollection(
            value = "a case where a static method with a string parameter is called",
            stringDefinitions = {
                    @StringDefinitions(
                            expectedLevel = CONSTANT,
                            expectedStrings = "java.lang.Integer"
                    )
            })
    public void fromStaticMethodWithParam() {
        analyzeString(InterproceduralTestMethods.getFQClassName(JAVA_LANG, "Integer"));
    }

    private String getRuntimeClassName() {
        return "java.lang.Runtime";
    }

    private String getStringBuilderClassName() {
        return "java.lang.StringBuilder";
    }

    private String getSimpleStringBuilderClassName() {
        return "StringBuilder";
    }

    /**
     * Returns "[packageName].[className]".
     */
    public static String getFQClassName(String packageName, String className) {
        return packageName + "." + className;
    }

}
