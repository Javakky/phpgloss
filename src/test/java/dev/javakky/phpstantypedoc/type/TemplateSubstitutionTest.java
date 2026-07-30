package dev.javakky.phpstantypedoc.type;

import junit.framework.TestCase;

/**
 * 総称形に添える複数形の作り方を確認する。要素アクセスのために足すものなので、
 * 足しても型が増えない形は足さない。
 */
public final class TemplateSubstitutionTest extends TestCase {
    public void testAddsThePluralFormOfAList() {
        assertEquals("\\App\\User[]", TemplateSubstitution.pluralFormOf("list<\\App\\User>"));
    }

    public void testAddsThePluralFormOfAKeyedArray() {
        assertEquals("\\App\\User[]", TemplateSubstitution.pluralFormOf("array<\\string, \\App\\User>"));
    }

    /** `array<X>` は `X[]` と同じ型。足すと表示に同じ型が二重に並ぶ。 */
    public void testSkipsThePluralFormWhenItIsTheSameTypeAsTheGenericForm() {
        assertNull(TemplateSubstitution.pluralFormOf("array<\\App\\User>"));
    }

    public void testAddsNothingForATypeWithoutTypeArguments() {
        assertNull(TemplateSubstitution.pluralFormOf("\\App\\User[]"));
        assertNull(TemplateSubstitution.pluralFormOf("\\App\\User"));
    }
}
