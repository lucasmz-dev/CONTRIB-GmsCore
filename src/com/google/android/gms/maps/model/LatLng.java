/*
 * Copyright (c) 2014 μg Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.maps.model;

import android.os.Parcel;
import org.microg.safeparcel.SafeParcelUtil;
import org.microg.safeparcel.SafeParcelable;
import org.microg.safeparcel.SafeParceled;

/**
 * An immutable class representing a pair of latitude and longitude coordinates, stored as degrees.
 */
public final class LatLng implements SafeParcelable {
    @SafeParceled(1)
    private final int versionCode;
    /**
     * Latitude, in degrees. This value is in the range [-90, 90].
     */
    @SafeParceled(2)
    public final double latitude;
    /**
     * Longitude, in degrees. This value is in the range [-180, 180).
     */
    @SafeParceled(3)
    public final double longitude;

    /**
     * This constructor is dirty setting the final fields to make the compiler happy.
     * In fact, those are replaced by their real values later using SafeParcelUtil.
     */
    private LatLng() {
        versionCode = -1;
        latitude = longitude = 0;
    }

    private LatLng(Parcel in) {
        this();
        SafeParcelUtil.readObject(this, in);
    }

    /**
     * Constructs a LatLng with the given latitude and longitude, measured in degrees.
     *
     * @param latitude  The point's latitude. This will be clamped to between -90 degrees and
     *                  +90 degrees inclusive.
     * @param longitude The point's longitude. This will be normalized to be within -180 degrees
     *                  inclusive and +180 degrees exclusive.
     */
    public LatLng(double latitude, double longitude) {
        this.versionCode = 1;
        this.latitude = Math.max(-90, Math.min(90, latitude));
        if ((-180 <= longitude) && (longitude < 180)) {
            this.longitude = longitude;
        } else {
            this.longitude = ((360 + (longitude - 180) % 360) % 360 - 180);
        }
    }

    /**
     * Tests if this LatLng is equal to another.
     * <p/>
     * Two points are considered equal if and only if their latitudes are bitwise equal and their
     * longitudes are bitwise equal. This means that two {@link LatLng}s that are very near, in
     * terms of geometric distance, might not be considered {@code .equal()}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        LatLng latLng = (LatLng) o;

        if (Double.compare(latLng.latitude, latitude) != 0)
            return false;
        if (Double.compare(latLng.longitude, longitude) != 0)
            return false;

        return true;
    }

    @Override
    public final int hashCode() {
        long lat = Double.doubleToLongBits(latitude);
        int tmp = 31 + (int) (lat ^ lat >>> 32);
        long lon = Double.doubleToLongBits(longitude);
        return tmp * 31 + (int) (lon ^ lon >>> 32);
    }

    @Override
    public String toString() {
        return "lat/lng: (" + latitude + "," + longitude + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        SafeParcelUtil.writeObject(this, out, flags);
    }

    public static Creator<LatLng> CREATOR = new Creator<LatLng>() {
        public LatLng createFromParcel(Parcel source) {
            return new LatLng(source);
        }

        public LatLng[] newArray(int size) {
            return new LatLng[size];
        }
    };
}
