/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2007-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.chrono;

import static java.time.temporal.ChronoUnit.SECONDS;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;

/**
 * A date-time with a time-zone in the calendar neutral API.
 * <p>
 * {@code ZoneChronoDateTime} is an immutable representation of a date-time with a time-zone.
 * This class stores all date and time fields, to a precision of nanoseconds,
 * as well as a time-zone and zone offset.
 * <p>
 * The purpose of storing the time-zone is to distinguish the ambiguous case where
 * the local time-line overlaps, typically as a result of the end of daylight time.
 * Information about the local-time can be obtained using methods on the time-zone.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @param <D> the concrete type for the date of this date-time
 * @since 1.8
 */
final class ChronoZonedDateTimeImpl<D extends ChronoLocalDate<D>>
        implements ChronoZonedDateTime<D>, Serializable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -5261813987200935591L;

    /**
     * The local date-time.
     */
    private final ChronoLocalDateTimeImpl<D> dateTime;
    /**
     * The zone offset.
     */
    private final ZoneOffset offset;
    /**
     * The zone ID.
     */
    private final ZoneId zone;

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance from a local date-time using the preferred offset if possible.
     *
     * @param localDateTime  the local date-time, not null
     * @param zone  the zone identifier, not null
     * @param preferredOffset  the zone offset, null if no preference
     * @return the zoned date-time, not null
     */
    static <R extends ChronoLocalDate<R>> ChronoZonedDateTime<R> ofBest(
            ChronoLocalDateTimeImpl<R> localDateTime, ZoneId zone, ZoneOffset preferredOffset) {
        Objects.requireNonNull(localDateTime, "localDateTime");
        Objects.requireNonNull(zone, "zone");
        if (zone instanceof ZoneOffset) {
            return new ChronoZonedDateTimeImpl<>(localDateTime, (ZoneOffset) zone, zone);
        }
        ZoneRules rules = zone.getRules();
        LocalDateTime isoLDT = LocalDateTime.from(localDateTime);
        List<ZoneOffset> validOffsets = rules.getValidOffsets(isoLDT);
        ZoneOffset offset;
        if (validOffsets.size() == 1) {
            offset = validOffsets.get(0);
        } else if (validOffsets.size() == 0) {
            ZoneOffsetTransition trans = rules.getTransition(isoLDT);
            localDateTime = localDateTime.plusSeconds(trans.getDuration().getSeconds());
            offset = trans.getOffsetAfter();
        } else {
            if (preferredOffset != null && validOffsets.contains(preferredOffset)) {
                offset = preferredOffset;
            } else {
                offset = validOffsets.get(0);
            }
        }
        Objects.requireNonNull(offset, "offset");  // protect against bad ZoneRules
        return new ChronoZonedDateTimeImpl<>(localDateTime, offset, zone);
    }

    /**
     * Obtains an instance from an instant using the specified time-zone.
     *
     * @param chrono  the chronology, not null
     * @param instant  the instant, not null
     * @param zone  the zone identifier, not null
     * @return the zoned date-time, not null
     */
    @SuppressWarnings("rawtypes")
    static ChronoZonedDateTimeImpl<?> ofInstant(Chronology chrono, Instant instant, ZoneId zone) {
        ZoneRules rules = zone.getRules();
        ZoneOffset offset = rules.getOffset(instant);
        Objects.requireNonNull(offset, "offset");  // protect against bad ZoneRules
        LocalDateTime ldt = LocalDateTime.ofEpochSecond(instant.getEpochSecond(), instant.getNano(), offset);
        ChronoLocalDateTimeImpl<?> cldt = (ChronoLocalDateTimeImpl<?>) chrono.localDateTime(ldt);
        return new ChronoZonedDateTimeImpl(cldt, offset, zone);
    }

    /**
     * Obtains an instance from an {@code Instant}.
     *
     * @param instant  the instant to create the date-time from, not null
     * @param zone  the time-zone to use, validated not null
     * @return the zoned date-time, validated not null
     */
    private ChronoZonedDateTimeImpl<D> create(Instant instant, ZoneId zone) {
        return (ChronoZonedDateTimeImpl<D>)ofInstant(toLocalDate().getChronology(), instant, zone);
    }

    //-----------------------------------------------------------------------
    /**
     * Constructor.
     *
     * @param dateTime  the date-time, not null
     * @param offset  the zone offset, not null
     * @param zone  the zone ID, not null
     */
    private ChronoZonedDateTimeImpl(ChronoLocalDateTimeImpl<D> dateTime, ZoneOffset offset, ZoneId zone) {
        this.dateTime = Objects.requireNonNull(dateTime, "dateTime");
        this.offset = Objects.requireNonNull(offset, "offset");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    //-----------------------------------------------------------------------
    public ZoneOffset getOffset() {
        return offset;
    }

    @Override
    public ChronoZonedDateTime<D> withEarlierOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(LocalDateTime.from(this));
        if (trans != null && trans.isOverlap()) {
            ZoneOffset earlierOffset = trans.getOffsetBefore();
            if (earlierOffset.equals(offset) == false) {
                return new ChronoZonedDateTimeImpl<D>(dateTime, earlierOffset, zone);
            }
        }
        return this;
    }

    @Override
    public ChronoZonedDateTime<D> withLaterOffsetAtOverlap() {
        ZoneOffsetTransition trans = getZone().getRules().getTransition(LocalDateTime.from(this));
        if (trans != null) {
            ZoneOffset offset = trans.getOffsetAfter();
            if (offset.equals(getOffset()) == false) {
                return new ChronoZonedDateTimeImpl<D>(dateTime, offset, zone);
            }
        }
        return this;
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoLocalDateTime<D> toLocalDateTime() {
        return dateTime;
    }

    public ZoneId getZone() {
        return zone;
    }

    public ChronoZonedDateTime<D> withZoneSameLocal(ZoneId zone) {
        return ofBest(dateTime, zone, offset);
    }

    @Override
    public ChronoZonedDateTime<D> withZoneSameInstant(ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return this.zone.equals(zone) ? this : create(dateTime.toInstant(offset), zone);
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isSupported(TemporalField field) {
        return field instanceof ChronoField || (field != null && field.isSupportedBy(this));
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoZonedDateTime<D> with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            switch (f) {
                case INSTANT_SECONDS: return plus(newValue - toEpochSecond(), SECONDS);
                case OFFSET_SECONDS: {
                    ZoneOffset offset = ZoneOffset.ofTotalSeconds(f.checkValidIntValue(newValue));
                    return create(dateTime.toInstant(offset), zone);
                }
            }
            return ofBest(dateTime.with(field, newValue), zone, offset);
        }
        return (ChronoZonedDateTime<D>)(toLocalDate().getChronology().ensureChronoZonedDateTime(field.adjustInto(this, newValue)));
    }

    //-----------------------------------------------------------------------
    @Override
    public ChronoZonedDateTime<D> plus(long amountToAdd, TemporalUnit unit) {
        if (unit instanceof ChronoUnit) {
            return with(dateTime.plus(amountToAdd, unit));
        }
        return (ChronoZonedDateTime<D>)(toLocalDate().getChronology().ensureChronoZonedDateTime(unit.addTo(this, amountToAdd)));   /// TODO: Generics replacement Risk!
    }

    //-----------------------------------------------------------------------
    @Override
    public long periodUntil(Temporal endDateTime, TemporalUnit unit) {
        if (endDateTime instanceof ChronoZonedDateTime == false) {
            throw new DateTimeException("Unable to calculate period between objects of two different types");
        }
        @SuppressWarnings("unchecked")
        ChronoZonedDateTime<D> end = (ChronoZonedDateTime<D>) endDateTime;
        if (toLocalDate().getChronology().equals(end.toLocalDate().getChronology()) == false) {
            throw new DateTimeException("Unable to calculate period between two different chronologies");
        }
        if (unit instanceof ChronoUnit) {
            end = end.withZoneSameInstant(offset);
            return dateTime.periodUntil(end.toLocalDateTime(), unit);
        }
        return unit.between(this, endDateTime);
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.CHRONO_ZONE_DATE_TIME_TYPE, this);
    }

    /**
     * Defend against malicious streams.
     * @return never
     * @throws InvalidObjectException always
     */
    private Object readResolve() throws ObjectStreamException {
        throw new InvalidObjectException("Deserialization via serialization delegate");
    }

    void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(dateTime);
        out.writeObject(offset);
        out.writeObject(zone);
    }

    static ChronoZonedDateTime<?> readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        ChronoLocalDateTime<?> dateTime = (ChronoLocalDateTime<?>) in.readObject();
        ZoneOffset offset = (ZoneOffset) in.readObject();
        ZoneId zone = (ZoneId) in.readObject();
        return dateTime.atZone(offset).withZoneSameLocal(zone);
        // TODO: ZDT uses ofLenient()
    }

    //-------------------------------------------------------------------------
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ChronoZonedDateTime) {
            return compareTo((ChronoZonedDateTime<?>) obj) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toLocalDateTime().hashCode() ^ getOffset().hashCode() ^ Integer.rotateLeft(getZone().hashCode(), 3);
    }

    @Override
    public String toString() {
        String str = toLocalDateTime().toString() + getOffset().toString();
        if (getOffset() != getZone()) {
            str += '[' + getZone().toString() + ']';
        }
        return str;
    }


}