package edu.umd.cs.findbugs.sarif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugPattern;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.FindBugs2;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.Project;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.classfile.Global;
import edu.umd.cs.findbugs.classfile.IAnalysisCache;
import edu.umd.cs.findbugs.classfile.impl.ClassFactory;
import edu.umd.cs.findbugs.classfile.impl.ClassPathImpl;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class PlaceholderTest {
    private SarifBugReporter reporter;
    private StringWriter writer;

    @Before
    public void setup() {
        Project project = new Project();
        reporter = new SarifBugReporter(project);
        writer = new StringWriter();
        reporter.setWriter(new PrintWriter(writer));
        reporter.setPriorityThreshold(Priorities.IGNORE_PRIORITY);
        DetectorFactoryCollection.resetInstance(new DetectorFactoryCollection());
        IAnalysisCache analysisCache = ClassFactory.instance().createAnalysisCache(new ClassPathImpl(), reporter);
        Global.setAnalysisCacheForCurrentThread(analysisCache);
        FindBugs2.registerBuiltInAnalysisEngines(analysisCache);
        AnalysisContext analysisContext = new AnalysisContext(project) {
            public boolean isApplicationClass(@DottedClassName String className) {
                // treat all classes as application class, to report bugs in it
                return true;
            }
        };
        AnalysisContext.setCurrentAnalysisContext(analysisContext);
    }

    @After
    public void teardown() {
        AnalysisContext.removeCurrentAnalysisContext();
        Global.removeAnalysisCacheForCurrentThread();
    }

    @Test
    public void testFormatWithKey() throws ClassNotFoundException, JsonProcessingException {
        BugPattern bugPattern = new BugPattern("BUG_TYPE", "abbrev", "category", false, "describing about this bug type...",
                "describing about this bug type with value {0.givenClass} and {1.name}", "detailText", null, 0);
        DetectorFactoryCollection.instance().registerBugPattern(bugPattern);

        JavaClass clazz = Repository.lookupClass(PlaceholderTest.class);
        Method method = Arrays.stream(clazz.getMethods()).filter(m -> m.getName().equals("testFormatWithKey")).findFirst().get();
        reporter.reportBug(new BugInstance(bugPattern.getType(), bugPattern.getPriorityAdjustment()).addClassAndMethod(clazz, method));
        reporter.finish();

        String json = writer.toString();
        ObjectMapper objectMapper = new ObjectMapper();
        SarifSchema210 schema = objectMapper.readValue(json, SarifSchema210.class);
        Set<ReportingDescriptor> rules = schema.getRuns().get(0).getTool().getDriver().getRules();
        String defaultText = rules.stream().findFirst().get().getMessageStrings().getAdditionalProperties().get("default").getText();
        assertThat("key in placeholders are removed",
                defaultText, is("describing about this bug type with value {0} and {1}"));

        List<String> arguments = schema.getRuns().get(0).getResults().get(0).getMessage().getArguments();
        assertThat("BugAnnotation has been formatted by the key in placeholder",
                arguments.get(0), is("PlaceholderTest"));
        assertThat("BugAnnotation has been formatted by the key in placeholder",
                arguments.get(1), is("testFormatWithKey"));
    }
}
