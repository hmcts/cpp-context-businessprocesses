package uk.gov.moj.cpp.businessprocesses.shared;

import static java.time.LocalDate.parse;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import uk.gov.justice.listing.events.Hearing;
import uk.gov.justice.listing.events.HearingDay;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.builder.CompareToBuilder;

public class StartDateAndTimeComparator implements Comparator<Hearing>, Serializable {

    private static final long serialVersionUID = 3819316995768775427L;

    @Override
    public int compare(final Hearing hearing1, final Hearing hearing2) {

        final CompareToBuilder compareToBuilder = new CompareToBuilder()
                .append(parse(hearing1.getStartDate()), parse(hearing2.getStartDate()));

        final HearingDay firstFutureHearingDayForHearing1 = getFirstFutureHearingDay(hearing1.getHearingDays());
        final HearingDay firstFutureHearingDayForHearing2 = getFirstFutureHearingDay(hearing2.getHearingDays());

        if (isNull(firstFutureHearingDayForHearing1) && nonNull(firstFutureHearingDayForHearing2)) {
            compareToBuilder.append(firstFutureHearingDayForHearing2.getStartTime(), null);
        } else if (nonNull(firstFutureHearingDayForHearing1) && isNull(firstFutureHearingDayForHearing2)) {
            compareToBuilder.append(null, firstFutureHearingDayForHearing1.getStartTime());
        } else if (nonNull(firstFutureHearingDayForHearing1)) {
            compareToBuilder.append(firstFutureHearingDayForHearing1.getStartTime(), firstFutureHearingDayForHearing2.getStartTime());
        }
        return compareToBuilder.toComparison();
    }

    private HearingDay getFirstFutureHearingDay(List<HearingDay> hearingDays) {
        return hearingDays.stream()
                .filter(hearingDay -> hearingDay.getStartTime().toLocalDateTime().isAfter(LocalDateTime.now()))
                .findFirst().orElse(null);

    }
}
