# RAG retrieval test set

`questions.json` holds 40 questions over four public PDFs, ten per document. Each question lists:
- the page or pages that answer it
- 1 to 3 phrases copied from that page
- a type: `concept`, `fact`, `exact-term` or `number`

`RagRetrievalEvalTest` scores the retriever against it.

| Id | Document | Kind | Pages | Get it |
|---|---|---|---|---|
| `attention` | Attention Is All You Need | research paper | 15 | <https://arxiv.org/pdf/1706.03762v7> |
| `oauth2` | RFC 6749, The OAuth 2.0 Authorization Framework | technical standard | 76 | Print <https://www.rfc-editor.org/rfc/rfc6749.txt> to PDF with one page per form feed; the RFC has no official PDF |
| `scheduling` | Operating Systems: Three Easy Pieces, ch. 7 | textbook chapter | 13 | <https://pages.cs.wisc.edu/~remzi/OSTEP/cpu-sched.pdf> |
| `nist-auth` | NIST SP 800-63B | security guidelines | 80 | <https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-63b.pdf> |

Save them under the `file` names in `questions.json`. Each entry also records the SHA-256 of the copy the questions were written against. A copy that differs byte for byte, such as a re-rendered RFC, only prints a note. If its text differs, the harness's phrase check fails that question instead of scoring it wrongly.

The RFC and NIST documents are long enough that the old full-text prompt never saw most of them, because each PDF was cut at 50,000 characters, around page 21 and page 23. Most of their questions have answers past that point.

## How the questions were made

1. One agent per document wrote 16 candidates with a fixed mix of types. In the long documents, at least 9 of them had to be answered past the 50,000-character cut.
2. Three independent reviewers per document tried to break each candidate:
   - **grounding:** are the phrases on the page, and does it answer the question?
   - **one answer:** is there one clear answer, not also given in full elsewhere?
   - **realism:** would a student ask it?

   A candidate stayed only if grounding passed and at least two of the three reviewers passed it.
3. A selector picked 10 balanced questions per document. None of the 8 candidates a reviewer failed made the final set.
4. A script, separate from the agents, checked every phrase against the page text.

## Running it

```bash
RAG_EVAL_PDF_DIR=/path/to/pdfs GEMINI_API_KEY=... mvn test -Dtest=RagRetrievalEvalTest
```

Embeddings are cached in `target/rag-eval-cache`, so a re-run only spends quota on token counting. The report goes to `target/rag-eval-report.md` and per-question results to `target/rag-eval-results.json`.
