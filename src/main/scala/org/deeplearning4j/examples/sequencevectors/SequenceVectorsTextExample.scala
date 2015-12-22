package org.deeplearning4j.examples.sequencevectors

import org.deeplearning4j.models.embeddings.WeightLookupTable
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable
import org.deeplearning4j.models.embeddings.learning.impl.elements.SkipGram
import org.deeplearning4j.models.embeddings.loader.VectorsConfiguration
import org.deeplearning4j.models.sequencevectors.SequenceVectors
import org.deeplearning4j.models.sequencevectors.iterators.AbstractSequenceIterator
import org.deeplearning4j.models.sequencevectors.transformers.impl.SentenceTransformer
import org.deeplearning4j.models.word2vec.VocabWord
import org.deeplearning4j.models.word2vec.wordstore.VocabConstructor
import org.deeplearning4j.models.word2vec.wordstore.inmemory.AbstractCache
import org.deeplearning4j.text.sentenceiterator.BasicLineIterator
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor
import org.deeplearning4j.text.tokenization.tokenizerfactory.{DefaultTokenizerFactory, TokenizerFactory}
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource

/**
 * This is example of abstract sequence of data is learned using AbstractVectors. In this example, we use text sentences as Sequences, and VocabWords as SequenceElements.
 * So, this example is  a simple demonstration how one can learn distributed representation of data sequences.
 *
 * For training on different data, you can extend base class SequenceElement, and feed model with your Iterable. Aslo, please note, in this case model persistence should be handled on your side.
 *
 * *************************************************************************************************
 * PLEASE NOTE: THIS EXAMPLE REQUIRES DL4J/ND4J VERSIONS >= rc3.8 TO COMPILE SUCCESSFULLY
 * *************************************************************************************************
 * @author raver119@gmail.com
 */
object SequenceVectorsTextExample {

    lazy val logger = LoggerFactory.getLogger(SequenceVectorsTextExample.getClass)

    @throws[Exception]
    def main(args: Array[String]): Unit = {

        val resource = new ClassPathResource("raw_sentences.txt")
        val file = resource.getFile()

        val vocabCache: AbstractCache[VocabWord]  = new AbstractCache.Builder[VocabWord]().build()

        /*
            First we build line iterator
         */
        val underlyingIterator: BasicLineIterator = new BasicLineIterator(file)


        /*
            Now we need the way to convert lines into Sequences of VocabWords.
            In this example that's SentenceTransformer
         */
        val t: TokenizerFactory = new DefaultTokenizerFactory()
        t.setTokenPreProcessor(new CommonPreprocessor())

        val transformer: SentenceTransformer = new SentenceTransformer.Builder()
                .iterator(underlyingIterator)
                .tokenizerFactory(t)
                .build()


        /*
            And we pack that transformer into AbstractSequenceIterator
         */
        val sequenceIterator: AbstractSequenceIterator[VocabWord]  = new AbstractSequenceIterator.Builder[VocabWord](transformer)
                .build()


        /*
            Now we should build vocabulary out of sequence iterator.
            We can skip this phase, and just set AbstractVectors.resetModel(TRUE), and vocabulary will be mastered internally
        */
        val constructor: VocabConstructor[VocabWord] = new VocabConstructor.Builder[VocabWord]()
                .addSource(sequenceIterator, 5)
                .setTargetVocabCache(vocabCache)
                .build()

        constructor.buildJointVocabulary(false, true)

        /*
            Time to build WeightLookupTable instance for our new model
        */

        val lookupTable: WeightLookupTable[VocabWord] = new InMemoryLookupTable.Builder[VocabWord]()
                .lr(0.025)
                .vectorLength(150)
                .useAdaGrad(false)
                .cache(vocabCache)
                .build()

         /*
             reset model is viable only if you're setting AbstractVectors.resetModel() to false
             if set to True - it will be called internally
        */
        lookupTable.resetWeights(true)

        /*
            Now we can build AbstractVectors model, that suits our needs
         */
        val vectors: SequenceVectors[VocabWord] = new SequenceVectors.Builder[VocabWord](new VectorsConfiguration())
                // minimum number of occurencies for each element in training corpus. All elements below this value will be ignored
                // Please note: this value has effect only if resetModel() set to TRUE, for internal model building. Otherwise it'll be ignored, and actual vocabulary content will be used
                .minWordFrequency(5)

                // WeightLookupTable
                .lookupTable(lookupTable)

                // abstract iterator that covers training corpus
                .iterate(sequenceIterator)

                // vocabulary built prior to modelling
                .vocabCache(vocabCache)

                // batchSize is the number of sequences being processed by 1 thread at once
                // this value actually matters if you have iterations > 1
                .batchSize(250)

                // number of iterations over batch
                .iterations(1)

                // number of iterations over whole training corpus
                .epochs(1)

                // if set to true, vocabulary will be built from scratches internally
                // otherwise externally provided vocab will be used
                .resetModel(false)


                /*
                    These two methods define our training goals. At least one goal should be set to TRUE.
                 */
                .trainElementsRepresentation(true)
                .trainSequencesRepresentation(false)

                /*
                    Specifies elements learning algorithms. SkipGram, for example.
                 */
                .elementsLearningAlgorithm(new SkipGram[VocabWord]())

                .build()

        /*
            Now, after all options are set, we just call fit()
         */
        vectors.fit()

        /*
            As soon as fit() exits, model considered built, and we can test it.
            Please note: all similarity context is handled via SequenceElement's labels, so if you're using AbstractVectors to build models for complex
            objects/relations please take care of Labels uniqueness and meaning for yourself.
         */
        val sim: Double = vectors.similarity("day", "night")
        logger.info("Day/night similarity: " + sim)

    }
}