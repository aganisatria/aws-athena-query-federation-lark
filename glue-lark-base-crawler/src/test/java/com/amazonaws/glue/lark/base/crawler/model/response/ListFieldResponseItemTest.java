package com.amazonaws.glue.lark.base.crawler.model.response;

import com.amazonaws.glue.lark.base.crawler.model.enums.UITypeEnum;
import org.junit.Test;
import software.amazon.awssdk.utils.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ListFieldResponseItemTest {

    @Test
    public void getUIType_shouldReturnCorrectEnum() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertEquals(UITypeEnum.TEXT, item.getUIType());

        ListFieldResponse.FieldItem itemUnknown = ListFieldResponse.FieldItem.builder().uiType("NonExistent").build();
        assertEquals(UITypeEnum.UNKNOWN, itemUnknown.getUIType());
    }

    @Test
    public void getUiTypeString_shouldReturnRawUiType() {
        String rawUiType = "FancyCustomType";
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType(rawUiType).build();
        assertEquals(rawUiType, item.getUiTypeString());
    }

    @Test
    public void getFormulaGlueCatalogType_notFormulaType_shouldReturnNull() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertNull(item.getFormulaGlueCatalogType());
    }

    @Test
    public void getFormulaGlueCatalogType_formula_noPropertyType_shouldDefaultToTextGlueType() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .uiType("Formula")
                .property(null)
                .build();
        assertEquals(UITypeEnum.TEXT.getGlueCatalogType(null), item.getFormulaGlueCatalogType());
    }

    @Test
    public void getFormulaGlueCatalogType_formula_propertyTypeNotMap_shouldDefaultToTextGlueType() {
        Map<String, Object> property = new HashMap<>();
        property.put("type", "NotAMapObject");
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .uiType("Formula")
                .property(property)
                .build();
        assertEquals(UITypeEnum.TEXT.getGlueCatalogType(null), item.getFormulaGlueCatalogType());
    }

    @Test
    public void getFormulaGlueCatalogType_formula_propertyTypeMapNoUiType_shouldDefaultToTextGlueType() {
        Map<String, Object> property = new HashMap<>();
        property.put("type", Map.of("some_other_key", "value"));
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .uiType("Formula")
                .property(property)
                .build();
        assertEquals(UITypeEnum.TEXT.getGlueCatalogType(null), item.getFormulaGlueCatalogType());
    }

    @Test
    public void getFormulaGlueCatalogType_formula_withValidPropertyType_shouldReturnCorrectGlueType() {
        Map<String, Object> property = new HashMap<>();
        property.put("type", Map.of("ui_type", "Number"));
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .uiType("Formula")
                .property(property)
                .build();
        assertEquals(UITypeEnum.NUMBER.getGlueCatalogType(null), item.getFormulaGlueCatalogType());
    }

    @Test
    public void getLookupSourceFieldAndTableId_notLookupType_shouldReturnNull() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertNull(item.getLookupSourceFieldAndTableId());
    }

    @Test
    public void getLookupSourceFieldAndTableId_lookup_missingProperties_shouldReturnNull() {
        ListFieldResponse.FieldItem item1 = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(null).build();
        assertNull(item1.getLookupSourceFieldAndTableId());

        Map<String, Object> propertyNoTargetField = Map.of("filter_info", Map.of("target_table", "t1"));
        ListFieldResponse.FieldItem item2 = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(propertyNoTargetField).build();
        assertNull(item2.getLookupSourceFieldAndTableId());

        Map<String, Object> propertyNoFilterInfo = Map.of("target_field", "f1");
        ListFieldResponse.FieldItem item3 = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(propertyNoFilterInfo).build();
        assertNull(item3.getLookupSourceFieldAndTableId());

        Map<String, Object> propertyFilterInfoNotMap = Map.of("target_field", "f1", "filter_info", "not_a_map");
        ListFieldResponse.FieldItem item4 = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(propertyFilterInfoNotMap).build();
        assertNull(item4.getLookupSourceFieldAndTableId());

        Map<String, Object> propertyFilterInfoMapNoTargetTable = Map.of("target_field", "f1", "filter_info", Map.of("other_key", "v1"));
        ListFieldResponse.FieldItem item5 = ListFieldResponse.FieldItem.builder().uiType("Lookup").property(propertyFilterInfoMapNoTargetTable).build();
        assertNull(item5.getLookupSourceFieldAndTableId());
    }

    @Test
    public void getLookupSourceFieldAndTableId_lookup_validProperties_shouldReturnPair() {
        String targetField = "field_abc";
        String targetTable = "table_xyz";
        Map<String, Object> property = Map.of(
                "target_field", targetField,
                "filter_info", Map.of("target_table", targetTable)
        );
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder()
                .uiType("Lookup")
                .property(property)
                .build();
        Pair<String, String> result = item.getLookupSourceFieldAndTableId();
        assertNotNull(result);
        assertEquals(targetField, result.left());
        assertEquals(targetTable, result.right());
    }

    @Test
    public void blackListField_shouldReturnTrue_forButtonOrStage() {
        ListFieldResponse.FieldItem buttonItem = ListFieldResponse.FieldItem.builder().uiType("Button").build();
        assertTrue(buttonItem.blackListField());

        ListFieldResponse.FieldItem stageItem = ListFieldResponse.FieldItem.builder().uiType("Stage").build();
        assertTrue(stageItem.blackListField());
    }

    @Test
    public void blackListField_shouldReturnTrue_forFormulaReturningButtonOrStage() {
        Map<String, Object> propertyButton = Map.of("type", Map.of("ui_type", "Button"));
        ListFieldResponse.FieldItem formulaButton = ListFieldResponse.FieldItem.builder().uiType("Formula").property(propertyButton).build();
        assertTrue(formulaButton.blackListField());

        Map<String, Object> propertyStage = Map.of("type", Map.of("ui_type", "Stage"));
        ListFieldResponse.FieldItem formulaStage = ListFieldResponse.FieldItem.builder().uiType("Formula").property(propertyStage).build();
        assertTrue(formulaStage.blackListField());
    }

    @Test
    public void blackListField_shouldReturnFalse_forOtherTypes() {
        ListFieldResponse.FieldItem textItem = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertFalse(textItem.blackListField());

        Map<String, Object> propertyText = Map.of("type", Map.of("ui_type", "Text"));
        ListFieldResponse.FieldItem formulaText = ListFieldResponse.FieldItem.builder().uiType("Formula").property(propertyText).build();
        assertFalse(formulaText.blackListField());
    }

    @Test
    public void getFormulaGlueCatalogUITypeEnum_formula_withValidPropertyType_shouldReturnCorrectEnum() {
        Map<String, Object> property = Map.of("type", Map.of("ui_type", "Number"));
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Formula").property(property).build();
        assertEquals(UITypeEnum.NUMBER, item.getFormulaGlueCatalogUITypeEnum());
    }

    @Test
    public void getFormulaGlueCatalogUITypeEnum_formula_noPropertyType_shouldDefaultToTextEnum() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Formula").property(Collections.emptyMap()).build();
        assertEquals(UITypeEnum.TEXT, item.getFormulaGlueCatalogUITypeEnum());
    }

    @Test
    public void getFormulaGlueCatalogUITypeEnum_notFormula_shouldDefaultToTextEnum() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertEquals(UITypeEnum.TEXT, item.getFormulaGlueCatalogUITypeEnum());
    }

    @Test
    public void getFormulaType_formula_withValidPropertyType_shouldReturnCorrectUiTypeString() {
        Map<String, Object> correctProperty = Map.of("type", Map.of("ui_type", "Checkbox"));
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Formula").property(correctProperty).build();
        assertEquals(UITypeEnum.CHECKBOX.getUiType(), item.getFormulaType());
    }

    @Test
    public void getFormulaType_formula_noPropertyType_shouldDefaultToTextUiType() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Formula").property(Collections.emptyMap()).build();
        assertEquals(UITypeEnum.TEXT.getUiType(), item.getFormulaType());
    }

    @Test
    public void getFormulaType_notFormula_shouldReturnNull() {
        ListFieldResponse.FieldItem item = ListFieldResponse.FieldItem.builder().uiType("Text").build();
        assertNull(item.getFormulaType());
    }
}