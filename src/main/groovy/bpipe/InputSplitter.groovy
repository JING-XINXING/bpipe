package bpipe

import groovy.util.logging.Log;
import java.util.regex.Matcher;

/**
 * Implements logic for splitting input files into groups by sample using simple
 * wildcard patterns.  The goal is to provide a simpler way for people to specify
 * how file names are related to sample names than asking people to either do it 
 * manually or to write complicated regexs or other logic.
 * <p>
 * The logic used here is based on simple file globbing (using the * operator) but with
 * a special % wildcard operator that indicates the portion of the file name that 
 * represents the sample name.  The user can specify a mask that matches their 
 * file names such as sequence_%_*.txt and the input files will be grouped into those 
 * that have the same digit in the % position, then ordered based on the matches to
 * the wild card position.  
 * <p>
 * The result is that the user can just shove a whole directory files in (even ones
 * that do not match their "mask" at all and they will be filtered to the set that
 * matches the mask and then split into groups based on sample, each group of which can 
 * then be individually handled separately by the pipeline.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class InputSplitter {

    /**
     * Splits the given inputs up according to the specified pattern 
     * where a splitting point is indicated by a % character and a 
     * grouping and ordering point is indicated by a * character.  Thus all 
     * inputs with the differing section matched by % are split into separate groups 
     * while all those with matching sections covered by * are grouped and ordered
     * numerically if they contain leading numbers, otherwise they are ordered
     * lexically.
     * 
     * @param pattern
     * @param input
     * @return
     */
	Map split(String pattern, input) {
		// Replace the two special characters (*) and (%) with 
		// regex groups
        Map splitMap = this.convertPattern(pattern)
        String regex = splitMap.pattern
        List<Integer> splitGroups = splitMap.splits
        
        def unsortedResult = [:]
        for(String inp in Utils.box(input).collect { new File(it).name }) {
            Matcher m = (inp =~ regex)
            if(!m)
			    continue
                
            String group = "all"
            if(splitGroups) {
                 group = splitGroups.collect { m[0][it+1] }.join(".")
            }
            log.fine "The group:  $group"
            if(!unsortedResult.containsKey(group))
                unsortedResult[group] = []
                
            unsortedResult[group] << inp
		}
        
		// We now have all the inputs keyed on the part matching the split char, 
		// however we want to also sort them 
        Map sortedResult = [:]
        unsortedResult.each {  k,v ->
            sortedResult[k] = this.sortNumericThenLexically(regex, splitGroups, v)
		}
	}
    
    /**
     * Sort by using the groups matched by the given regex
     * first numerically and then lexically while skipping
     * the given group.
     * <p>
     * NB: this is horrifically inefficient.  It rematches the same
     * regexes with every sort comparison.  If it ever matters
     * it can be greatly optimized.
     * 
     * @param v
     * @return    a reordered version of the input list
     */
    List sortNumericThenLexically(String regex, List<Integer> skipGroups, List v) {
		return v.sort { String i1, String i2 ->
            // Match 
			Matcher m1 = (i1 =~ regex)
			Matcher m2 = (i2 =~ regex)
            
            // Convert the regex matches to lists
            List g1 = [], g2 = []
            int count = 0
            for(m in m1[0]) {
                log.fine "==>  $m"
                if(count++ in skipGroups)
                    continue
                    
			    g1 << m
            }
            count = 0
            for(m in m2[0]) {
                if(count++ in skipGroups)
                    continue
			    g2 << m
            }
            
			// Work through each group and return on the first difference
            log.fine "groups:  $g1  $g2"
			for(int i=0; i<g1.size(); ++i) {
                String s1=g1[i], s2=g2[i]
                
				if(s1 == s2)
				    continue
                
				log.fine "Compare:  $s1 : $s1"
                
                // Numeric 
				if(s1.matches("^[0-9].*") && s2.matches("^[0-9].*")) {
					Integer n1 = Integer.parseInt((s1 =~ "^([0-9])")[0][0] )
					Integer n2 = Integer.parseInt((s2 =~ "^([0-9])")[0][0] )
                    return n1.compareTo(n2)
				}
                else
    				return s1.compareTo(s2)
			}
            
			log.fine "$i1 == $i2"
            return 0
		}    }
    
    /**
     * Converts the given pattern from simplified Bpipe 
     * split format to a normal regular expression
     * format.
     * 
     * @return     a Map containing two elements:
     * 
     *                  - the regex pattern generated to split and match groups 
     *                    within the inputs 
     *                  - a list of integers indicating the indexes of the
     *                    groups within the regex that are split patterns
     *                    as opposed to grouping patterns
     */
	Map convertPattern(String pattern) {
        
		// Find the characters flanking the % and * and use those as
		// pattern delimiters
        List<Integer> percs = pattern.findIndexValues { it == "%" }.collect { it as Integer }
        
        // int percPos = pattern.indexOf('%')
//        if(percPos == -1)
//		    throw new PipelineError("A sample split pattern must contain a % character to indicate the splitting point")
		
		List<Integer> starPos = pattern.findIndexValues { it == "*" }.collect { it as Integer }
        
		log.info "Found * pattern at $starPos and % at $percs"
        
        List sorted = (starPos + percs).sort()
		
        log.info "Sorted: $sorted"
        
		List<Integer> percGroups = sorted.findIndexValues { it in percs }.collect { it as Integer }
        if(!percGroups) {
		    sorted = starPos
            sorted.sort()
        }
        
		log.info "% groups are at " + percGroups
        
		// Figure out flanking characters.  Rather than making the user 
		// specify which characters delimit their expression we just use the 
		// characters on either side of the wild cards
        def result = new StringBuilder()
        def lastPos = -1
        def lastRight = ""
        for(c in sorted) {
            log.fine "----- $c ----"
			def leftFlank = new StringBuilder()
            int lpos = c
            while(lpos && (lastPos < 0 || (lpos-1 != lastPos+1))) {
                leftFlank.append(pattern[lpos-1]) 
                --lpos
            }
            leftFlank = leftFlank.toString().reverse()
           
            //			def leftFlank = c && (lastPos < 0 || (c-1 != lastPos+1)) ? pattern[c-1] : ""
            
			def rightFlank = c<pattern.size()-1 ? pattern[c+1].replaceAll(/\./,/\\./) : ""
            
            log.fine "Position $c : left=$leftFlank right=$rightFlank"
            
            // If there is no right hand flank then don't include
			// any exclusion in the pattern.  If there is one,
			// exclude characters matching the right flanking character
//            def wildcard = rightFlank ? "[^"+rightFlank+"]*" : ".*" 
            def wildcard = rightFlank ? ".*?" : ".*"
            def group = "($wildcard)"
            result << leftFlank + group + rightFlank
			log.fine "chunk for $c:  " + (leftFlank + group + rightFlank)
            lastPos = c
            lastRight = rightFlank
		}
        int lastRenderedPos = lastPos+1+(lastRight.size()?1:0)
        if(lastRenderedPos < pattern.size()-1)
            result << pattern.substring(lastRenderedPos, pattern.size())
        
        Map resultMap = [ 
            pattern: result.toString(),
            splits: percGroups
        ]
	    log.info "Result is $resultMap"
        return resultMap
	}
}
