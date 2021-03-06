/*
 * Copyright 2011 DBpedia Spotlight Development Team
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Check our project website for information on how to acknowledge the authors and how to contribute to the project: http://spotlight.dbpedia.org
 */

package org.dbpedia.spotlight.model

import org.dbpedia.spotlight.disambiguate._
import org.dbpedia.spotlight.exceptions.ConfigurationException
import org.dbpedia.spotlight.log.SpotlightLog
import org.dbpedia.spotlight.model.SpotlightConfiguration.DisambiguationPolicy
import org.dbpedia.spotlight.model.SpotterConfiguration.SpotterPolicy
import org.dbpedia.spotlight.spot._
import org.dbpedia.spotlight.spot.ahocorasick.AhoCorasickSpotter

import scala.collection.JavaConversions._
import scala.io.Source

/**
 * This class contains many of the "defaults" for DBpedia Spotlight.
 * Maybe consider renaming to DefaultFactory or DBpediaSpotlightController
 *
 * @author pablomendes
 */
class SpotlightFactory(val configuration: SpotlightConfiguration) {
    val analyzer = configuration.analyzer
    assert(analyzer!=null)



    // The dbpedia resource factory is used every time a document is retrieved from the index.
    // We can use the index itself as provider, or we can use a database. whichever is faster.
    // If the factory is left null, BaseSearcher will use Lucene. Otherwise, it will use the factory.
    val dbpediaResourceFactory : DBpediaResourceFactory = configuration.getDBpediaResourceFactory

    val spotters = new java.util.LinkedHashMap[SpotterConfiguration.SpotterPolicy,Spotter]() // LinkedHashMap used to preserve order (needed in spotter())
    val disambiguators = new java.util.LinkedHashMap[SpotlightConfiguration.DisambiguationPolicy,ParagraphDisambiguatorJ]()

    //populate
    SpotlightLog.info(this.getClass, "Initiating spotters...")
    spotter()
    SpotlightLog.info(this.getClass, "Initiating disambiguators...")
    disambiguator()
    SpotlightLog.info(this.getClass, "Done.")

    def spotter(policy: SpotterConfiguration.SpotterPolicy) : Spotter = {
        if (policy == SpotterConfiguration.SpotterPolicy.Default) {
            if (spotters.isEmpty)
                throw new ConfigurationException("You have to specify at least one spotter implementation (besides Default) in the configuration file.")
            val innerSpotter = spotters.head._2
            val spotSelectors = Factory.SpotSelector.fromNameList(configuration.getSpotterConfiguration.config.getOrElse("org.dbpedia.spotlight.spot.selectors", ""))
            val defaultSpotter = if (spotSelectors.isEmpty) {
                innerSpotter
            } else {
                SpotterWithSelector.getInstance(innerSpotter, new ChainedSelector(spotSelectors))
            }
            defaultSpotter
        } else if(policy == SpotterConfiguration.SpotterPolicy.AhoCorasickSpotter) {
            val overlap = configuration.getSpotterConfiguration.config.getOrElse("org.dbpedia.spotlight.spot.allowOverlap", "false").equals("true")
            val caseSensitive = configuration.getSpotterConfiguration.config.getOrElse("org.dbpedia.spotlight.spot.caseSensitive", "false").equals("true")
            val sourceChunks = Source.fromFile(configuration.getSpotterConfiguration.getSpotterSurfaceForms)
            val spotter = AhoCorasickSpotter.fromSurfaceForms(sourceChunks.getLines(), caseSensitive, overlap)
            sourceChunks.close
            spotters.getOrElse(policy,spotter)
        } else if (policy == SpotterConfiguration.SpotterPolicy.NESpotter) {
            spotters.getOrElse(policy, new NESpotter(configuration.getSpotterConfiguration.getOpenNLPModelDir+"/"+configuration.getLanguage.toLowerCase+"/",configuration.getI18nLanguageCode.toLowerCase, configuration.getSpotterConfiguration.getOpenNLPModelsURI))
        } else if (policy == SpotterConfiguration.SpotterPolicy.KeyphraseSpotter) {
            spotters.getOrElse(policy, new KeaSpotter(configuration.getSpotterConfiguration.getKeaModel, configuration.getSpotterConfiguration.getKeaMaxNumberOfPhrases, configuration.getSpotterConfiguration.getKeaCutoff))
        } else if (policy == SpotterConfiguration.SpotterPolicy.SpotXmlParser) {
          new SpotXmlParser
        } else if (policy == SpotterConfiguration.SpotterPolicy.WikiMarkupSpotter) {
            new WikiMarkupSpotter
        } else {
            new WikiMarkupSpotter
        }
    }

    def spotter() : Spotter = {
        val spotterPolicies = configuration.getSpotterConfiguration.getSpotterPolicies
        spotterPolicies.foreach( policy => {
            if (policy != SpotterPolicy.Default)
                spotters.put(policy, spotter(policy))
        })
        val default = spotter(SpotterPolicy.Default)
        spotters.put(SpotterPolicy.Default, default)
        default
    }

    def disambiguator() : ParagraphDisambiguatorJ = {
        val disambiguatorPolicies = configuration.getDisambiguatorConfiguration.getDisambiguatorPolicies
        disambiguatorPolicies.foreach( policy => {
            disambiguators.put(policy, disambiguator(policy))
        })
        val default = disambiguator(disambiguatorPolicies.get(0)) // default is first in configuration list
        disambiguators.put(DisambiguationPolicy.Default,default)
        default
    }

    def disambiguator(policy: SpotlightConfiguration.DisambiguationPolicy) : ParagraphDisambiguatorJ = {
         disambiguator(SpotlightConfiguration.DisambiguationPolicy.Occurrences)
    }


    object DBpediaResource {
        def from(dbpediaID : String) : DBpediaResource = dbpediaResourceFactory.from(dbpediaID)
        def from(dbpediaResource : DBpediaResource) = dbpediaResourceFactory.from(dbpediaResource.uri)
    }

}
