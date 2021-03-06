/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.parse_thicket.matching;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.textsimilarity.LemmaPair;

public class ParseTreePath {
  private String mainPOS;

  private List<String> lemmas;

  private List<String> POSs;
  //order number of a word in a sentence
  private List<Integer> wordUniqueCodes;

  private int startPos;

  private int endPos;

  private int size;

  private ParseTreePathMatcher parseTreeMatcher;

  private LemmaFormManager lemmaFormManager;

  private GeneralizationListReducer generalizationListReducer;

  public ParseTreePath() {
  }

  public ParseTreePath(List<String> lemmas, List<String> POSs, int startPos,
      int endPos) {
    this.lemmas = lemmas;
    this.POSs = POSs;
    this.startPos = startPos;
    this.endPos = endPos;

  }

  // constructor which takes lemmas and POS as lists so that phrases can be
  // conveniently specified.
  // usage: stand-alone runs
  public ParseTreePath(String mPOS, String[] lemmas, String[] POSss) {
    this.mainPOS = mPOS;
    this.lemmas = new ArrayList<String>();
    for (String l : lemmas) {
      this.lemmas.add(l);
    }
    if (mPOS.equals("SENTENCE")){
    	for(int i=0; i<lemmas.length; i++){
    		wordUniqueCodes.add(this.lemmas.get(i).hashCode());
    	}
    }
    
    this.POSs = new ArrayList<String>();
    for (String p : POSss) {
      this.POSs.add(p);
    }
  }

  // constructor which takes lemmas and POS as lists so that phrases can be
  // conveniently specified.
  // usage: stand-alone runs
  public ParseTreePath(String mPOS, List<String> lemmas, List<String> POSss) {
    this.mainPOS = mPOS;
    this.lemmas = lemmas;
    this.POSs = POSss;

  }

  // Before:
  // [0(S-At home we like to eat great pizza deals), 0(PP-At home), 0(IN-At),
  // 3(NP-home), 3(NN-home), 8(NP-we),
  // 8(PRP-we), 11(VP-like to eat great pizza deals), 11(VBP-like), 16(S-to eat
  // great pizza deals), 16(VP-to eat great
  // pizza deals),
  // 16(TO-to), 19(VP-eat great pizza deals), 19(VB-eat), 23(NP-great pizza
  // deals), 23(JJ-great), 29(NN-pizza),
  // 35(NNS-deals)]

  // After:
  // [S [IN-At NP-home NP-we VBP-like ], PP [IN-At NP-home ], IN [IN-At ], NP
  // [NP-home ], NN [NP-home ], NP [NP-we ],
  // PRP [NP-we ], VP [VBP-like TO-to VB-eat JJ-great ], VBP [VBP-like ], S
  // [TO-to VB-eat JJ-great NN-pizza ], VP
  // [TO-to VB-eat JJ-great NN-pizza ], TO [TO-to ], VP [VB-eat JJ-great
  // NN-pizza NNS-deals ],
  // VB [VB-eat ], NP [JJ-great NN-pizza NNS-deals ], JJ [JJ-great ], NN
  // [NN-pizza ], NNS [NNS-deals ]]

  public List<ParseTreePath> buildChunks(List<LemmaPair> parseResults) {
    List<ParseTreePath> chunksResults = new ArrayList<ParseTreePath>();
    for (LemmaPair chunk : parseResults) {
      String[] lemmasAr = chunk.getLemma().split(" ");
      List<String> poss = new ArrayList<String>(), lems = new ArrayList<String>();
      for (String lem : lemmasAr) {
        lems.add(lem);
        // now looking for POSs for individual word
        for (LemmaPair chunkCur : parseResults) {
          if (chunkCur.getLemma().equals(lem)
              &&
              // check that this is a proper word in proper position
              chunkCur.getEndPos() <= chunk.getEndPos()
              && chunkCur.getStartPos() >= chunk.getStartPos()) {
            poss.add(chunkCur.getPOS());
            break;
          }
        }
      }
      if (lems.size() != poss.size()) {
        System.err.println("lems.size()!= poss.size()");
      }
      if (lems.size() < 2) { // single word phrase, nothing to match
        continue;
      }
      ParseTreePath ch = new ParseTreePath(lems, poss, chunk.getStartPos(),
          chunk.getEndPos());
      ch.setMainPOS(chunk.getPOS());
      chunksResults.add(ch);
    }
    return chunksResults;
  }

  public List<List<ParseTreePath>> matchTwoSentencesGivenPairLists(
      List<LemmaPair> sent1Pairs, List<LemmaPair> sent2Pairs) {

    List<ParseTreePath> chunk1List = buildChunks(sent1Pairs);
    List<ParseTreePath> chunk2List = buildChunks(sent2Pairs);

    List<List<ParseTreePath>> sent1GrpLst = groupChunksAsParses(chunk1List);
    List<List<ParseTreePath>> sent2GrpLst = groupChunksAsParses(chunk2List);

    System.out.println("=== Grouped chunks 1 " + sent1GrpLst);
    System.out.println("=== Grouped chunks 2 " + sent2GrpLst);

    return matchTwoSentencesGroupedChunks(sent1GrpLst, sent2GrpLst);
  }

  // groups noun phrases, verb phrases, propos phrases etc. for separate match

  public List<List<ParseTreePath>> groupChunksAsParses(
      List<ParseTreePath> parseResults) {
    List<ParseTreePath> np = new ArrayList<ParseTreePath>(), vp = new ArrayList<ParseTreePath>(), prp = new ArrayList<ParseTreePath>(), sbarp = new ArrayList<ParseTreePath>(), pp = new ArrayList<ParseTreePath>(), adjp = new ArrayList<ParseTreePath>(), whadvp = new ArrayList<ParseTreePath>(), restOfPhrasesTypes = new ArrayList<ParseTreePath>();
    List<List<ParseTreePath>> results = new ArrayList<List<ParseTreePath>>();
    for (ParseTreePath ch : parseResults) {
      String mainPos = ch.getMainPOS().toLowerCase();

      if (mainPos.equals("s")) {
        continue;
      }
      if (mainPos.equals("np")) {
        np.add(ch);
      } else if (mainPos.equals("vp")) {
        vp.add(ch);
      } else if (mainPos.equals("prp")) {
        prp.add(ch);
      } else if (mainPos.equals("pp")) {
        pp.add(ch);
      } else if (mainPos.equals("adjp")) {
        adjp.add(ch);
      } else if (mainPos.equals("whadvp")) {
        whadvp.add(ch);
      } else if (mainPos.equals("sbar")) {
        sbarp.add(ch);
      } else {
        restOfPhrasesTypes.add(ch);
      }

    }
    results.add(np);
    results.add(vp);
    results.add(prp);
    results.add(pp);
    results.add(adjp);
    results.add(whadvp);
    results.add(restOfPhrasesTypes);

    return results;

  }

  // main function to generalize two expressions grouped by phrase types
  // returns a list of generalizations for each phrase type with filtered
  // sub-expressions
  public List<List<ParseTreePath>> matchTwoSentencesGroupedChunks(
      List<List<ParseTreePath>> sent1, List<List<ParseTreePath>> sent2) {
    List<List<ParseTreePath>> results = new ArrayList<List<ParseTreePath>>();
    // first irerate through component
    for (int comp = 0; comp < 2 && // just np & vp
        comp < sent1.size() && comp < sent2.size(); comp++) {
      List<ParseTreePath> resultComps = new ArrayList<ParseTreePath>();
      // then iterate through each phrase in each component
      for (ParseTreePath ch1 : sent1.get(comp)) {
        for (ParseTreePath ch2 : sent2.get(comp)) { // simpler version
          ParseTreePath chunkToAdd = parseTreeMatcher
              .generalizeTwoGroupedPhrasesRandomSelectHighestScoreWithTransforms(
                  ch1, ch2);

          if (!lemmaFormManager.mustOccurVerifier(ch1, ch2, chunkToAdd)) {
            continue; // if the words which have to stay do not stay, proceed to
                      // other elements
          }
          Boolean alreadyThere = false;
          for (ParseTreePath chunk : resultComps) {
            if (chunk.equalsTo(chunkToAdd)) {
              alreadyThere = true;
              break;
            }

            if (parseTreeMatcher
                .generalizeTwoGroupedPhrasesRandomSelectHighestScore(chunk,
                    chunkToAdd).equalsTo(chunkToAdd)) {
              alreadyThere = true;
              break;
            }
          }

          if (!alreadyThere) {
            resultComps.add(chunkToAdd);
          }

          List<ParseTreePath> resultCompsReduced = generalizationListReducer
              .applyFilteringBySubsumption(resultComps);
          // if (resultCompsReduced.size() != resultComps.size())
          // System.out.println("reduction of gen list occurred");
        }
      }
      results.add(resultComps);
    }

    return results;
  }

  public Boolean equals(ParseTreePath ch) {
    List<String> lems = ch.getLemmas();
    List<String> poss = ch.POSs;

    if (this.lemmas.size() <= lems.size())
      return false; // sub-chunk should be shorter than chunk

    for (int i = 0; i < lems.size() && i < this.lemmas.size(); i++) {
      if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
          poss.get(i))))
        return false;
    }
    return true;
  }

  // 'this' is super - chunk of ch, ch is sub-chunk of 'this'
  public Boolean isASubChunk(ParseTreePath ch) {
    List<String> lems = ch.getLemmas();
    List<String> poss = ch.POSs;

    if (this.lemmas.size() < lems.size())
      return false; // sub-chunk should be shorter than chunk

    for (int i = 0; i < lems.size() && i < this.lemmas.size(); i++) {
      if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
          poss.get(i))))
        return false;
    }
    return true;
  }

  public Boolean equalsTo(ParseTreePath ch) {
    List<String> lems = ch.getLemmas();
    List<String> poss = ch.POSs;
    if (this.lemmas.size() != lems.size() || this.POSs.size() != poss.size())
      return false;

    for (int i = 0; i < lems.size(); i++) {
      if (!(this.lemmas.get(i).equals(lems.get(i)) && this.POSs.get(i).equals(
          poss.get(i))))
        return false;
    }

    return true;
  }

  public String toString() {
    String buf = " [";
    if (mainPOS != null)
      buf = mainPOS + " [";
    for (int i = 0; i < lemmas.size() && i < POSs.size() // && i<=3
    ; i++) {
      buf += POSs.get(i) + "-" + lemmas.get(i) + " ";
    }
    return buf + "]";
  }

  public int compareTo(ParseTreePath o) {
    if (this.size > o.size)
      return -1;
    else
      return 1;

  }

  public String listToString(List<List<ParseTreePath>> chunks) {
    StringBuffer buf = new StringBuffer();
    if (chunks.get(0).size() > 0) {
      buf.append(" np " + chunks.get(0).toString());
    }
    if (chunks.get(1).size() > 0) {
      buf.append(" vp " + chunks.get(1).toString());
    }
    if (chunks.size() < 3) {
      return buf.toString();
    }
    if (chunks.get(2).size() > 0) {
      buf.append(" prp " + chunks.get(2).toString());
    }
    if (chunks.get(3).size() > 0) {
      buf.append(" pp " + chunks.get(3).toString());
    }
    if (chunks.get(4).size() > 0) {
      buf.append(" adjp " + chunks.get(4).toString());
    }
    if (chunks.get(5).size() > 0) {
      buf.append(" whadvp " + chunks.get(5).toString());
    }
    /*
     * if (mainPos.equals("np")) np.add(ch); else if (mainPos.equals( "vp"))
     * vp.add(ch); else if (mainPos.equals( "prp")) prp.add(ch); else if
     * (mainPos.equals( "pp")) pp.add(ch); else if (mainPos.equals( "adjp"))
     * adjp.add(ch); else if (mainPos.equals( "whadvp")) whadvp.add(ch);
     */
    return buf.toString();
  }

  public List<List<ParseTreePath>> obtainParseTreeChunkListByParsingList(
      String toParse) {
    List<List<ParseTreePath>> results = new ArrayList<List<ParseTreePath>>();
    // if (toParse.endsWith("]]]")){
    // toParse = toParse.replace("[[","").replace("]]","");
    // }
    toParse = toParse.replace(" ]], [ [", "&");
    String[] phraseTypeFragments = toParse.trim().split("&");
    for (String toParseFragm : phraseTypeFragments) {
      toParseFragm = toParseFragm.replace("],  [", "#");

      List<ParseTreePath> resultsPhraseType = new ArrayList<ParseTreePath>();
      String[] indivChunks = toParseFragm.trim().split("#");
      for (String expr : indivChunks) {
        List<String> lems = new ArrayList<String>(), poss = new ArrayList<String>();
        expr = expr.replace("[", "").replace(" ]", "");
        String[] pairs = expr.trim().split(" ");
        for (String word : pairs) {
          word = word.replace("]]", "").replace("]", "");
          String[] pos_lem = word.split("-");
          lems.add(pos_lem[1].trim());
          poss.add(pos_lem[0].trim());
        }
        ParseTreePath ch = new ParseTreePath();
        ch.setLemmas(lems);
        ch.setPOSs(poss);
        resultsPhraseType.add(ch);
      }
      results.add(resultsPhraseType);
    }
    System.out.println(results);
    return results;

    // 2.1 | Vietnam <b>embassy</b> <b>in</b> <b>Israel</b>: information on how
    // to get your <b>visa</b> at Vietnam
    // <b>embassy</b> <b>in</b> <b>Israel</b>. <b>...</b> <b>Spain</b>.
    // Scotland. Sweden. Slovakia. Switzerland. T
    // [Top of Page] <b>...</b>
    // [[ [NN-* IN-in NP-israel ], [NP-* IN-in NP-israel ], [NP-* IN-* TO-* NN-*
    // ], [NN-visa IN-* NN-* IN-in ]], [
    // [VB-get NN-visa IN-* NN-* IN-in .-* ], [VBD-* IN-* NN-* NN-* .-* ], [VB-*
    // NP-* ]]]

  }

  public void setMainPOS(String mainPOS) {
    this.mainPOS = mainPOS;
  }

  public String getMainPOS() {
    return mainPOS;
  }

  public List<String> getLemmas() {
    return lemmas;
  }

  public void setLemmas(List<String> lemmas) {
    this.lemmas = lemmas;
  }

  public List<String> getPOSs() {
    return POSs;
  }

  public void setPOSs(List<String> pOSs) {
    POSs = pOSs;
  }

  public ParseTreePathMatcher getParseTreeMatcher() {
    return parseTreeMatcher;
  }

}
