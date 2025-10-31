package uk.gov.moj.cpp.businessprocesses.shared;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import uk.gov.justice.listing.events.Defendant;
import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.listing.events.HearingDay;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class HearingHelper {
    private static final Comparator<HearingDay> compareByStartDateTime = Comparator.comparing(HearingDay::getStartTime);

    public static Hearing getLatestHearing(final List<Hearing> hearingList) {
        if (isEmpty(hearingList)) {
            return null;
        }
        final List<uk.gov.justice.listing.events.Hearing> allocatedHearings = hearingList.stream()
                .filter(uk.gov.justice.listing.events.Hearing::getAllocated)
                .collect(toList());

        if (isNotEmpty(allocatedHearings)) {
            allocatedHearings.forEach(hearing -> hearing.getHearingDays().sort(compareByStartDateTime));
            allocatedHearings.sort(new StartDateAndTimeComparator());
            return allocatedHearings.get(0);
        }
        return null;
    }

    public static String getHearingDate(final Hearing hearing) {
        if (hearing == null || isEmpty(hearing.getHearingDays()) || hearing.getHearingDays().get(0) == null) {
            return null;
        }
        final HearingDay hearingDay = hearing.getHearingDays().get(0);
        return hearingDay.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }

    public static String getCustodyTimeLimit(final uk.gov.justice.core.courts.Hearing hearing) {
        if (hearing == null || isEmpty(hearing.getProsecutionCases())) {
            return null;
        }
        return hearing.getProsecutionCases().stream()
                .flatMap(prosecutionCase -> prosecutionCase.getDefendants().stream())
                .flatMap(defendant -> defendant.getOffences().stream())
                .map(offence -> offence.getCustodyTimeLimit())
                .filter(Objects::nonNull)
                .map(custodyTimeLimit -> custodyTimeLimit.getTimeLimit())
                .findFirst()
                .orElse(null);
    }

    public static String getCustodyTimeLimit(final List<Hearing> hearings) {
        if (isEmpty(hearings)) {
            return null;
        }
        return hearings.stream()
                .flatMap(hearing -> hearing.getListedCases().stream())
                .flatMap(listedCase -> listedCase.getDefendants().stream())
                .map(Defendant::getCustodyTimeLimit)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }
}
