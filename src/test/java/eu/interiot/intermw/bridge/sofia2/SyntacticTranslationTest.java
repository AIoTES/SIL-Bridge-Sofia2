package eu.interiot.intermw.bridge.sofia2;

import eu.interiot.translators.syntax.sofia2.Sofia2Translator;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;

public class SyntacticTranslationTest {
	
	@Test
	public void testTranslation() throws Exception {

        File resourcesDirectory = new File("src/test/resources/SOFIA2");

        FilenameFilter jsonFilenameFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.endsWith(".json")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        
        File[] jsonFiles = resourcesDirectory.listFiles(jsonFilenameFilter);

        Sofia2Translator translator = new Sofia2Translator();

        for(File f : jsonFiles){
            System.out.println("************************");
            System.out.println("+++ Input file: " + f.getAbsolutePath() + " +++");
            System.out.println();

            String fileContents = new String(Files.readAllBytes(f.toPath()));

            System.out.println(fileContents);

            System.out.println();
            System.out.println("+++ RDF output: +++");
            System.out.println();

            //Translate towards INTER-MW

            Model jenaModel = translator.toJenaModelTransformed(fileContents);

            System.out.println(translator.printJenaModel(jenaModel, Lang.N3));

            System.out.println();
            System.out.println("+++ Reverse translation: +++");

            //Reverse the translation
            String jsonString = translator.toFormatXTransformed(jenaModel);

            System.out.println();
            System.out.println(translator.prettifyJsonString(jsonString));
            System.out.println();

        }

    }
	
}
