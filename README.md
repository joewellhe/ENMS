[![996.icu](https://img.shields.io/badge/link-996.icu-red.svg)](https://996.icu)


# ENMS
ENMS mtric is a ngram-based evaluation metric with semantic, the detail description in [Enhancing Ngram-based Metrics with Semantic for Better Evaluation of Abstract TextSummarization](https://arxiv.org/submit/2177410/view).
Our ENMS package is based on [ROUGE 2.0](https://github.com/RxNLP/ROUGE-2.0)

We add some field in rouge.properties, for ` rouge.metric`, it determines what ROUGE we choose.

e.g. `rouge.metric=SEMI-RN` represent we use semi-rouge-n metric, these metrics we can choose as followed:


- N for ROUGE-N
- SU for ROUGE-SU4
- L for ROUGE-L
- SEMI-RN for SMEI-ROUGE-N
- SRN for Soft-ROUGE-N
- SEMI-RSU for SEMI-ROUGE-SU4
- S-RSU for Soft-Rouge-SU4
 
Note that if you use SU OR L, ngram is a invalid field

For `alpha` field, it is a similarity parameter.

e.g. `alpha=0.75`

For `embedding` field, it determin the embedding file we use.

e.g. `embedding=small_embedding.txt`



## Installation
This section is the same with  installation in [ROUGE 2.0](https://github.com/RxNLP/ROUGE-2.0)

## Data
The data we use is AESOP task of TAC 2011.
In this pacakge, we just provide a simply case to run our ENMS package, if you want to verify the result in   [Enhancing Ngram-based Metrics with Semantic for Better Evaluation of Abstract TextSummarization](https://arxiv.org/submit/2177410/view), you should get access for TAC data 2011. You can get TAC data in [Past TAC Data](https://tac.nist.gov/data/index.html).

## Contact with us
Any question about ENMS package, you can send e-mail to hejiawei@hnu.edu.cn

