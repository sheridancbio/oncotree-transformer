package org.mskcc.cmo.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.mskcc.cmo.model.OncotreeConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OncotreeParser {

	private final static Logger LOG = LoggerFactory.getLogger(OncotreeParser.class);

	private final static String BASE_URL = "http://data.mskcc.org/ontologies/oncotree/";
	private final static String MAIN_TYPE = "http://data.mskcc.org/ontologies/oncotree/mainType";
	private final static String NCI = "http://data.mskcc.org/ontologies/oncotree/nci";
	private final static String UMLS = "http://data.mskcc.org/ontologies/oncotree/umls";
	private final static String NCCN = "http://data.mskcc.org/ontologies/oncotree/nccn";
	private final static String COLOR = "http://data.mskcc.org/ontologies/oncotree/color";

	private static Property MAIN_TYPE_P = null;
	private static Property NCI_P = null;
	private static Property UMLS_P = null;
	private static Property NCCN_P = null;
	private static Property COLOR_P = null;

	private static int id = 1;

	public static void main(String[] args) throws IOException{
		String file = "oncotree.csv";
		Model m = createOncotreeModel( parseOncotreeFile(file));
		m.write(System.out);
		m.write(new FileWriter("out"));
	}

	public static LinkedList<OncotreeConcept> parseOncotreeFile(String file){
		BufferedReader in = null;
		String strLine;
		LinkedList<OncotreeConcept> concepts = new LinkedList<OncotreeConcept>();
		boolean first = true;

		try{
			in = new BufferedReader(new FileReader(file));
			while((strLine=in.readLine())!=null){
				if(first){
					first = false;
					continue;
				}
				concepts.add( parseOncotreeConcept(strLine) );
			}
		}catch(Exception e){
			LOG.error("Exception while parsing file " + file, e);
		}
		return concepts;
	}

	public static Model createOncotreeModel(LinkedList<OncotreeConcept> concepts){
		Model model = ModelFactory.createDefaultModel();

		MAIN_TYPE_P = model.createProperty(MAIN_TYPE);
		model.getResource(MAIN_TYPE)
			.addProperty(RDFS.domain, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));

		COLOR_P = model.createProperty(COLOR);
		model.getResource(COLOR)
		.addProperty(RDFS.domain, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));

		UMLS_P = model.createProperty(UMLS);
		model.getResource(UMLS)
		.addProperty(RDFS.domain, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));

		NCI_P = model.createProperty(NCI);
		model.getResource(NCI)
		.addProperty(RDFS.domain, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));

		NCCN_P = model.createProperty(NCCN);
		model.getResource(NCCN)
		.addProperty(RDFS.domain, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));

		// concept scheme
		model.createResource("http://data.mskcc.org/ontologies/oncotree")
			.addProperty(RDF.type, SKOS.ConceptScheme)
			.addProperty(SKOS.hasTopConcept, model.createResource("http://data.mskcc.org/ontologies/oncotree/ONC000001")
											 .addProperty(SKOS.prefLabel, "Tissue"));

		// oncotree concept class
		model.createResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept")
			.addProperty(RDFS.subClassOf, SKOS.Concept);

		for(OncotreeConcept concept: concepts){
			addConceptIntoModel(concept, model);
		}

		return model;
	}

	private static void addConceptIntoModel(OncotreeConcept concept, Model model){

		// check if need to create primary
		ResIterator it = model.listResourcesWithProperty(SKOS.prefLabel, concept.getPrimary().substring(0, concept.getPrimary().indexOf("(")));

		if(concept.getIdx() == 1 && !it.hasNext()){
			String conceptURI = createConceptId();
			Resource c = model.createResource(conceptURI)
					.addProperty(SKOS.broader, "http://data.mskcc.org/ontologies/oncotree/ONC000001");
			model.getResource("http://data.mskcc.org/ontologies/oncotree/ONC000001")
				.addProperty(SKOS.narrower, c);
			addLabels(c, concept.getPrimary());
		}

		String conceptURI = createConceptId();
		Resource c = model.createResource(conceptURI);
		c.addProperty(RDF.type, model.getResource("http://data.mskcc.org/ontologies/oncotree/Oncotree_Concept"));
		if(concept.getColor()!=null && !StringUtils.isEmpty(concept.getColor())){
			c.addProperty(COLOR_P, concept.getColor());
		}

		if(concept.getMainType()!=null && !StringUtils.isEmpty(concept.getMainType())){
			c.addProperty(MAIN_TYPE_P, concept.getMainType());
		}

		if(concept.getNccn()!=null && !StringUtils.isEmpty(concept.getNccn())){
			c.addProperty(NCCN_P, concept.getNccn());
		}

		if(concept.getNci()!=null && !StringUtils.isEmpty(concept.getNci())){
			c.addProperty(NCI_P, concept.getNci());
		}

		if(concept.getUmls()!=null && !StringUtils.isEmpty(concept.getUmls())){
			c.addProperty(UMLS_P, concept.getUmls());
		}

		addParentAndChildren(c, concept, model);
	}

	private static Resource createConcept(Model model, Resource r, String label){
		String conceptUri = createConceptId();
		Resource c = model.createResource(conceptUri)
				.addProperty(SKOS.narrower, r);
		addLabels(c, label);
		return c;
	}

	private static void connectPrimaryToRootNode(Resource r, Model model){
		r.addProperty(SKOS.broader, model.getResource("http://data.mskcc.org/ontologies/oncotree/ONC000001"));
	}

	private static void addParentAndChildren(Resource r, OncotreeConcept concept, Model model){

		// Tissue parent (primary)
		if(concept.getIdx() == 0){
			r.addProperty(SKOS.broader, model.getResource("http://data.mskcc.org/ontologies/oncotree/ONC000001"));
			model.getResource("http://data.mskcc.org/ontologies/oncotree/ONC000001")
				.addProperty(SKOS.narrower, r);
			addLabels(r, concept.getPrimary());
			return;
		}

		// secondary
		if(concept.getIdx() == 1){
			ResIterator it = model.listResourcesWithProperty(SKOS.prefLabel, concept.getPrimary().substring(0, concept.getPrimary().indexOf("(")));
			if(it.hasNext()){
				Resource p = it.nextResource();
				r.addProperty(SKOS.broader, p);
				model.getResource(p.getURI())
					.addProperty(SKOS.narrower, r);
			}else{
				Resource p = createConcept(model, r, concept.getPrimary());
				r.addProperty(SKOS.broader, p);
				connectPrimaryToRootNode(p, model);
			}
			addLabels(r, concept.getSecondary());
			return;
		}

		// terciary
		if(concept.getIdx() == 2){
			ResIterator it = model.listResourcesWithProperty(SKOS.prefLabel, concept.getSecondary().substring(0, concept.getSecondary().indexOf("(")));
			if(it.hasNext()){
				Resource p = it.nextResource();
				r.addProperty(SKOS.broader, p);
				model.getResource(p.getURI())
					.addProperty(SKOS.narrower, r);
			}else{
				// creates secondary
				Resource secondary = createConcept(model, r, concept.getSecondary());
				r.addProperty(SKOS.broader, secondary);

				// checks if primary exist
				ResIterator itp = model.listResourcesWithProperty(SKOS.prefLabel, concept.getPrimary().substring(0, concept.getPrimary().indexOf("(")));
				if(itp.hasNext()){
					Resource primary = itp.nextResource();
					secondary.addProperty(SKOS.broader, primary);
					primary.addProperty(SKOS.narrower, secondary);
				}
				else{
					Resource primary = createConcept(model, secondary, concept.getPrimary());
					secondary.addProperty(SKOS.broader, primary);
					connectPrimaryToRootNode(primary, model);
				}
			}
			addLabels(r, concept.getTerciary());
			return;
		}

		// quaternary
		if(concept.getIdx() == 3){
			ResIterator it = model.listResourcesWithProperty(SKOS.prefLabel, concept.getTerciary().substring(0, concept.getTerciary().indexOf("(")));
			if(it.hasNext()){
				Resource p = it.nextResource();
				r.addProperty(SKOS.broader, p);
				model.getResource(p.getURI())
					.addProperty(SKOS.narrower, r);
			}else{
				// creates terciary
				Resource terciary = createConcept(model, r, concept.getTerciary());
				r.addProperty(SKOS.broader, terciary);

				// check if secondary exists
				ResIterator its = model.listResourcesWithProperty(SKOS.prefLabel, concept.getSecondary().substring(0, concept.getSecondary().indexOf("(")));
				if(its.hasNext()){
					Resource secondary = its.nextResource();
					terciary.addProperty(SKOS.broader, secondary);
					secondary.addProperty(SKOS.narrower, terciary);
				}
				else{
					// create secondary
					Resource secondary = createConcept(model, terciary, concept.getSecondary());
					terciary.addProperty(SKOS.broader, secondary);

					// check if primary exists
					ResIterator itp = model.listResourcesWithProperty(SKOS.prefLabel, concept.getPrimary().substring(0, concept.getPrimary().indexOf("(")));
					if(itp.hasNext()){
						Resource primary = itp.nextResource();
						secondary.addProperty(SKOS.broader, primary);
						primary.addProperty(SKOS.narrower, secondary);
					}
					else{
						Resource primary = createConcept(model, secondary, concept.getPrimary());
						secondary.addProperty(SKOS.broader, primary);
						connectPrimaryToRootNode(primary, model);
					}
				}

			}
			addLabels(r, concept.getQuaternary());
			return;
		}

		// quinternary
		if(concept.getIdx() == 4){
			ResIterator it = model.listResourcesWithProperty(SKOS.prefLabel, concept.getQuaternary().substring(0, concept.getQuaternary().indexOf("(")));
			if(it.hasNext()){
				Resource p = it.nextResource();
				r.addProperty(SKOS.broader, p);
				model.getResource(p.getURI())
					.addProperty(SKOS.narrower, r);
			}else{
				// create quaternary
				Resource quaternary = createConcept(model, r, concept.getQuaternary());
				r.addProperty(SKOS.broader, quaternary);

				// check if terciary exists
				ResIterator itt = model.listResourcesWithProperty(SKOS.prefLabel, concept.getTerciary().substring(0, concept.getTerciary().indexOf("(")));
				if(itt.hasNext()){
					Resource terciary = itt.nextResource();
					quaternary.addProperty(SKOS.broader, terciary);
					terciary.addProperty(SKOS.narrower, r);
				}else{
					// creates terciary
					Resource terciary = createConcept(model, quaternary, concept.getTerciary());
					r.addProperty(SKOS.broader, terciary);

					// check if secondary exists
					ResIterator its = model.listResourcesWithProperty(SKOS.prefLabel, concept.getSecondary().substring(0, concept.getSecondary().indexOf("(")));
					if(its.hasNext()){
						Resource secondary = its.nextResource();
						terciary.addProperty(SKOS.broader, secondary);
						secondary.addProperty(SKOS.narrower, terciary);
					}
					else{
						// create secondary
						Resource secondary = createConcept(model, terciary, concept.getSecondary());
						terciary.addProperty(SKOS.broader, secondary);

						// check if primary exists
						ResIterator itp = model.listResourcesWithProperty(SKOS.prefLabel, concept.getPrimary().substring(0, concept.getPrimary().indexOf("(")));
						if(itp.hasNext()){
							Resource primary = itp.nextResource();
							secondary.addProperty(SKOS.broader, primary);
							primary.addProperty(SKOS.narrower, secondary);
						}
						else{
							Resource primary = createConcept(model, secondary, concept.getPrimary());
							secondary.addProperty(SKOS.broader, primary);
							connectPrimaryToRootNode(primary, model);
						}
					}

				}
			}
			addLabels(r, concept.getQuinternary());
			return;
		}

	}

	private static void addLabels(Resource r, String label){
		label = label.trim();
		System.out.println(label);
		String code = label.substring(label.indexOf("(")+1, label.length()-1);
		String l = label.substring(0, label.indexOf("("));
		r.addProperty(SKOS.prefLabel, l)
			.addProperty(SKOS.notation, code);
	}

	private static String createConceptId(){
		++id;
		String conceptId = "ONC";
		int l = String.valueOf(id).length();
		for(int i= l;i<6;i++){
			conceptId += "0";
		}
		return BASE_URL + conceptId + id;
	}

	private static OncotreeConcept parseOncotreeConcept(String line){
		OncotreeConcept concept = new OncotreeConcept();
		String[] segments = line.split("\\|");
		int idx = -1;

		if(segments[0].startsWith("Peripheral Nervous System")){
			System.out.println("");
		}
		// primary
		if(segments.length>0 && !StringUtils.isEmpty(segments[0])){
			concept.setPrimary(segments[0]);
			++idx;
		}

		// secondary
		if(segments.length>1 && !StringUtils.isEmpty(segments[1])){
			concept.setSecondary(segments[1]);
			++idx;
		}

		// tertiary
		if(segments.length>2 && !StringUtils.isEmpty(segments[2])){
			concept.setTerciary(segments[2]);
			++idx;
		}

		// quaternary
		if(segments.length>3 && !StringUtils.isEmpty(segments[3])){
			concept.setQuaternary(segments[3]);
			++idx;
		}

		// quinternary
		if(segments.length>4 && !StringUtils.isEmpty(segments[4])){
			concept.setQuinternary(segments[4]);
			++idx;
		}

		// main type
		if(segments.length>5 && !StringUtils.isEmpty(segments[5])){
			concept.setMainType(segments[5]);
		}

		// color
		if(segments.length>6 && !StringUtils.isEmpty(segments[6])){
			concept.setColor(segments[6]);
		}

		// nci
		if(segments.length>7 && !StringUtils.isEmpty(segments[7])){
			concept.setNci(segments[7]);
		}

		// umls
		if(segments.length>8 && !StringUtils.isEmpty(segments[8])){
			concept.setUmls(segments[8]);
		}

		// nccn
		if(segments.length>9 && !StringUtils.isEmpty(segments[9])){
			concept.setNccn(segments[9]);
		}

		concept.setIdx(idx);

		return concept;
	}

}
