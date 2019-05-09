package org.dhis2.usescases.datasets.dataSetTable;

import com.squareup.sqlbrite2.BriteDatabase;

import org.dhis2.data.tuples.Pair;
import org.dhis2.utils.DateUtils;
import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.android.core.category.CategoryModel;
import org.hisp.dhis.android.core.category.CategoryOptionComboModel;
import org.hisp.dhis.android.core.category.CategoryOptionModel;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.dataelement.DataElementModel;
import org.hisp.dhis.android.core.dataset.DataSetCompleteRegistration;
import org.hisp.dhis.android.core.dataset.DataSetModel;
import org.hisp.dhis.android.core.period.PeriodType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

public class DataSetTableRepositoryImpl implements DataSetTableRepository {

    private final String DATA_ELEMENTS = "SELECT " +
            "DataElement.*," +
            "DataSetSection.sectionName," +
            "DataSetSection.sectionOrder " +
            "FROM DataElement " +
            "LEFT JOIN (" +
            "   SELECT " +
            "       Section.sortOrder AS sectionOrder," +
            "       Section.displayName AS sectionName," +
            "       Section.uid AS sectionId," +
            "       SectionDataElementLink.dataElement AS sectionDataElement, " +
            "       SectionDataElementLink.sortOrder AS sortOrder " +
            "   FROM Section " +
            "   JOIN SectionDataElementLink ON SectionDataElementLink.section = Section.uid " +
            "   WHERE Section.dataSet = ? " +
            ") AS DataSetSection ON DataSetSection.sectionDataElement = DataElement.uid " +
            "JOIN DataSetDataElementLink ON DataSetDataElementLink.dataElement = DataElement.uid " +
            "WHERE DataSetDataElementLink.dataSet = ? " +
            "ORDER BY DataSetSection.sectionOrder,DataSetSection.sortOrder";

    private final String DATA_SET = "SELECT DataSet.* FROM DataSet WHERE DataSet.uid = ?";
    private final String CATEGORY_OPTION = "SELECT CategoryOption.*, Category.uid AS category, section.displayName as SectionName," +
            "CategoryCategoryComboLink.sortOrder as sortOrder FROM CategoryOption " +
            "JOIN CategoryCategoryOptionLink ON CategoryCategoryOptionLink.categoryOption = CategoryOption.uid " +
            "JOIN Category ON CategoryCategoryOptionLink.category = Category.uid " +
            "JOIN CategoryCategoryComboLink ON CategoryCategoryComboLink.category = Category.uid " +
            "JOIN CategoryOptionComboCategoryOptionLink ON CategoryOptionComboCategoryOptionLink.categoryOption = CategoryOption.uid " +
            "JOIN DataElement ON DataElement.categoryCombo = CategoryCategoryComboLink.categoryCombo " +
            "JOIN DataSetDataElementLink ON DataSetDataElementLink.dataElement = DataElement.uid " +
            "JOIN CategoryCombo ON CategoryCombo.uid = DataElement.categoryCombo " +
            "LEFT JOIN (SELECT section.displayName, section.uid, SectionDataElementLINK.dataElement as dataelement FROM Section " +
            "JOIN SectionDataElementLINK ON SectionDataElementLink.section = Section.uid) as section on section.dataelement = DataElement.uid " +
            "WHERE DataSetDataElementLink.dataSet = ? " +
            "GROUP BY CategoryOption.uid, section.uid ORDER BY section.uid, CategoryCategoryComboLink.sortOrder, CategoryCategoryOptionLink.sortOrder";

    private final String CATEGORY_OPTION_COMBO = "SELECT CategoryOptionCombo.*,section.displayName as SectionName FROM CategoryOptionCombo " +
            "JOIN DataElement ON DataElement.categoryCombo = CategoryOptionCombo.categoryCombo " +
            "JOIN DataSetDataElementLink ON DataSetDataElementLink.dataElement = DataElement.uid " +
            "JOIN CategoryCategoryComboLink ON CategoryCategoryComboLink.categoryCombo = categoryOptionCombo.categoryCombo " +
            "JOIN CategoryOptionComboCategoryOptionLink ON CategoryOptionComboCategoryOptionLink.categoryOptionCombo = CategoryOptionCombo.uid " +
            "JOIN CategoryCategoryOptionLink ON CategoryCategoryOptionLink.categoryOption = CategoryOptionComboCategoryOptionLink.categoryOption " +
            "LEFT JOIN (SELECT section.displayName, section.uid, SectionDataElementLINK.dataElement as dataelement FROM Section " +
            "JOIN SectionDataElementLINK ON SectionDataElementLink.section = Section.uid) as section on section.dataelement = DataElement.uid " +
            "WHERE DataSetDataElementLink.dataSet = ? " +
            "GROUP BY section.uid, CategoryOptionCombo.uid  ORDER BY section.uid, CategoryCategoryComboLink.sortOrder, CategoryCategoryOptionLink.sortOrder";

    private final BriteDatabase briteDatabase;
    private final String dataSetUid;
    private final D2 d2;
    private final String periodId;
    private final String orgUnitUid;
    private final String catOptCombo;

    public DataSetTableRepositoryImpl(D2 d2, BriteDatabase briteDatabase, String dataSetUid,
                                      String periodId, String orgUnitUid, String catOptCombo) {
        this.d2 = d2;
        this.briteDatabase = briteDatabase;
        this.dataSetUid = dataSetUid;
        this.periodId = periodId;
        this.orgUnitUid = orgUnitUid;
        this.catOptCombo = catOptCombo;
    }

    @Override
    public Flowable<DataSetModel> getDataSet() {
        return briteDatabase.createQuery(DataSetModel.TABLE, DATA_SET, dataSetUid)
                .mapToOne(cursor -> DataSetModel.builder()
                        .uid(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.UID)))
                        .code(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.CODE)))
                        .name(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.NAME)))
                        .displayName(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.DISPLAY_NAME)))
                        .created(DateUtils.databaseDateFormat().parse(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.CREATED))))
                        .lastUpdated(DateUtils.databaseDateFormat().parse(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.LAST_UPDATED))))
                        .shortName(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.SHORT_NAME)))
                        .displayShortName(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.DISPLAY_SHORT_NAME)))
                        .description(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.DESCRIPTION)))
                        .displayDescription(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.DISPLAY_DESCRIPTION)))
                        .periodType(PeriodType.valueOf(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.PERIOD_TYPE))))
                        .categoryCombo(cursor.getString(cursor.getColumnIndex(DataSetModel.Columns.CATEGORY_COMBO)))
                        .mobile(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.MOBILE)) == 1)
                        .version(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.VERSION)))
                        .expiryDays(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.EXPIRY_DAYS)))
                        .timelyDays(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.TIMELY_DAYS)))
                        .notifyCompletingUser(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.NOTIFY_COMPLETING_USER)) == 1)
                        .openFuturePeriods(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.OPEN_FUTURE_PERIODS)))
                        .fieldCombinationRequired(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.FIELD_COMBINATION_REQUIRED)) == 1)
                        .validCompleteOnly(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.VALID_COMPLETE_ONLY)) == 1)
                        .noValueRequiresComment(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.NO_VALUE_REQUIRES_COMMENT)) == 1)
                        .skipOffline(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.SKIP_OFFLINE)) == 1)
                        .dataElementDecoration(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.DATA_ELEMENT_DECORATION)) == 1)
                        .renderAsTabs(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.RENDER_AS_TABS)) == 1)
                        .renderHorizontally(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.RENDER_HORIZONTALLY)) == 1)
                        .accessDataWrite(cursor.getInt(cursor.getColumnIndex(DataSetModel.Columns.ACCESS_DATA_WRITE)) == 1)
                        .build()).toFlowable(BackpressureStrategy.LATEST);
    }

    @Override
    public Flowable<Map<String, List<DataElementModel>>> getDataElements() {
        Map<String, List<DataElementModel>> map = new LinkedHashMap<>();
        return briteDatabase.createQuery(DataElementModel.TABLE, DATA_ELEMENTS, dataSetUid, dataSetUid)
                .mapToList(cursor -> {
                    DataElementModel dataElementModel = DataElementModel.create(cursor);
                    String section = cursor.getString(cursor.getColumnIndex("sectionName"));
                    if (section == null)
                        section = "NO_SECTION";
                    if (map.get(section) == null) {
                        map.put(section, new ArrayList<>());
                    }
                    map.get(section).add(dataElementModel);

                    return dataElementModel;
                })
                .flatMap(dataElementModels -> Observable.just(map)).toFlowable(BackpressureStrategy.LATEST);
    }

    @Override
    public Flowable<Map<String, List<CategoryOptionComboModel>>> getCatOptionCombo() {
        Map<String, List<CategoryOptionComboModel>> map = new HashMap<>();

        return briteDatabase.createQuery(CategoryOptionModel.TABLE, CATEGORY_OPTION_COMBO, dataSetUid)
                .mapToList(cursor -> {
                    CategoryOptionComboModel catOptionCombo = CategoryOptionComboModel.create(cursor);
                    String sectionName = cursor.getString(cursor.getColumnIndex("SectionName"));
                    if (sectionName == null)
                        sectionName = "NO_SECTION";
                    if (map.get(sectionName) == null) {
                        map.put(sectionName, new ArrayList<>());
                    }

                    map.get(sectionName).add(catOptionCombo);

                    return catOptionCombo;
                }).flatMap(categoryOptionComboModels -> Observable.just(map)).toFlowable(BackpressureStrategy.LATEST);
    }

    @Override
    public Flowable<Boolean> dataSetStatus() {
        DataSetCompleteRegistration dscr = d2.dataSetModule().dataSetCompleteRegistrations
                .byDataSetUid().eq(dataSetUid)
                .byAttributeOptionComboUid().eq(catOptCombo)
                .byOrganisationUnitUid().eq(orgUnitUid)
                .byPeriod().eq(periodId).one().get();
        return Flowable.defer(() -> Flowable.just(dscr != null && dscr.state() != State.TO_DELETE));
    }

    /**
     * returns Map key =
     */
    @Override
    public Flowable<Map<String, List<List<Pair<CategoryOptionModel, CategoryModel>>>>> getCatOptions() {
        Map<String, List<List<Pair<CategoryOptionModel, CategoryModel>>>> map = new HashMap<>();

        return briteDatabase.createQuery(CategoryOptionModel.TABLE, CATEGORY_OPTION, dataSetUid)
                .mapToList(cursor -> {
                    CategoryOptionModel catOption = CategoryOptionModel.create(cursor);
                    CategoryModel category = CategoryModel.builder().uid(cursor.getString(cursor.getColumnIndex("category"))).build();
                    String sectionName = cursor.getString(cursor.getColumnIndex("SectionName"));
                    if (sectionName == null)
                        sectionName = "NO_SECTION";
                    if (map.get(sectionName) == null) {
                        map.put(sectionName, new ArrayList<>());
                    }
                    if (map.get(sectionName).size() == 0) {
                        List<Pair<CategoryOptionModel, CategoryModel>> list = new ArrayList<>();
                        list.add(Pair.create(catOption, category));
                        map.get(sectionName).add(list);
                    } else {

                        if (map.get(sectionName).get(map.get(sectionName).size() - 1).get(0).val1().uid().equals(cursor.getString(cursor.getColumnIndex("category")))) {
                            map.get(sectionName).get(map.get(sectionName).size() - 1).add(Pair.create(catOption, category));
                        } else {
                            List<Pair<CategoryOptionModel, CategoryModel>> list = new ArrayList<>();
                            list.add(Pair.create(catOption, category));
                            map.get(sectionName).add(list);
                        }

                    }

                    return catOption;
                }).flatMap(categoryOptionComboModels -> Observable.just(map)).toFlowable(BackpressureStrategy.LATEST);
    }

    @Override
    public Flowable<String> getCatComboName(String catcomboUid) {
        return Flowable.just(d2.categoryModule().categoryOptionCombos.uid(catcomboUid).get().displayName());
    }


}
