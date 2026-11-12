package com.myriad.character.ability;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** Presentation-only evaluation of the documented formula notation; never executes skill damage. */
public final class AbilityFormulaText {
    private AbilityFormulaText() { }
    private static final String N="([0-9]+(?:\\.[0-9]+)?)";
    private static String replace(String text,String regex,Function<MatchResult,String> value){
        return Pattern.compile(regex).matcher(text).replaceAll(m->java.util.regex.Matcher.quoteReplacement(value.apply(m)));
    }
    private static double n(MatchResult m,int group){return Double.parseDouble(m.group(group));}
    private static String number(double n){return Math.abs(n-Math.rint(n))<.0001?String.format(Locale.ROOT,"%.0f",n):String.format(Locale.ROOT,"%.1f",n);}
    public static String evaluate(String description,int rawRank,double strength){
        int rank=Math.max(1,rawRank);
        String text=replace(description,N+"＋"+N+"\\(r－1\\)＋"+N+"%S",m->number(n(m,1)+n(m,2)*(rank-1)+n(m,3)*strength/100));
        text=replace(text,"\\["+N+"＋"+N+"\\(r－1\\)\\]%S",m->number((n(m,1)+n(m,2)*(rank-1))*strength/100));
        text=replace(text,"\\("+N+"＋r\\)%S",m->number((n(m,1)+rank)*strength/100));
        text=replace(text,N+"＋"+N+"\\(r－1\\)",m->number(n(m,1)+n(m,2)*(rank-1)));
        text=replace(text,N+"＋r",m->number(n(m,1)+rank));
        return text;
    }
}
