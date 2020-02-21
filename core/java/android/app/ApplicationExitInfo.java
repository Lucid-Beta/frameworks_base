/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningAppProcessInfo.Importance;
import android.icu.text.SimpleDateFormat;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.util.proto.WireTypeMismatchException;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Date;
import java.util.Objects;

/**
 * Describes the information of an application process's death.
 *
 * <p>
 * Application process could die for many reasons, for example {@link #REASON_LOW_MEMORY}
 * when it was killed by the ystem because it was running low on memory. Reason
 * of the death can be retrieved via {@link #getReason}. Besides the reason, there are a few other
 * auxiliary APIs like {@link #getStatus} and {@link #getImportance} to help the caller with
 * additional diagnostic information.
 * </p>
 *
 */
public final class ApplicationExitInfo implements Parcelable {

    /**
     * Application process died due to unknown reason.
     */
    public static final int REASON_UNKNOWN = 0;

    /**
     * Application process exit normally by itself, for example,
     * via {@link java.lang.System#exit}; {@link #getStatus} will specify the exit code.
     *
     * <p>Applications should normally not do this, as the system has a better knowledge
     * in terms of process management.</p>
     */
    public static final int REASON_EXIT_SELF = 1;

    /**
     * Application process died due to the result of an OS signal; for example,
     * {@link android.system.OsConstants#SIGKILL}; {@link #getStatus} will specify the signal
     * number.
     */
    public static final int REASON_SIGNALED = 2;

    /**
     * Application process was killed by the system low memory killer, meaning the system was
     * under memory pressure at the time of kill.
     *
     * <p class="note">
     * Not all devices support reporting {@link #REASON_LOW_MEMORY}; on a device with no such
     * support, when a process is killed due to memory pressure, the {@link #getReason} will return
     * {@link #REASON_SIGNALED} and {@link #getStatus} will return
     * the value {@link android.system.OsConstants#SIGKILL}.
     *
     * Application should use {@link ActivityManager#isLowMemoryKillReportSupported} to check
     * if the device supports reporting {@link #REASON_LOW_MEMORY} or not.
     * </p>
     */
    public static final int REASON_LOW_MEMORY = 3;

    /**
     * Application process died because of an unhandled exception in Java code.
     */
    public static final int REASON_CRASH = 4;

    /**
     * Application process died because of a native code crash.
     */
    public static final int REASON_CRASH_NATIVE = 5;

    /**
     * Application process was killed due to being unresponsive (ANR).
     */
    public static final int REASON_ANR = 6;

    /**
     * Application process was killed because of initialization failure,
     * for example, it took too long to attach to the system during the start,
     * or there was an error during initialization.
     */
    public static final int REASON_INITIALIZATION_FAILURE = 7;

    /**
     * Application process was killed due to a runtime permission change.
     */
    public static final int REASON_PERMISSION_CHANGE = 8;

    /**
     * Application process was killed by the system due to excessive resource usage.
     */
    public static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;

    /**
     * Application process was killed by the system for various other reasons,
     * for example, the application package got disabled by the user;
     * {@link #getDescription} will specify the cause given by the system.
     */
    public static final int REASON_OTHER = 10;

    /**
     * Application process kills subreason is unknown.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_UNKNOWN = 0;

    /**
     * Application process was killed because user quit it on the "wait for debugger" dialog;
     * this would be set when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_WAIT_FOR_DEBUGGER = 1;

    /**
     * Application process was killed by the activity manager because there were too many cached
     * processes; this would be set only when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_TOO_MANY_CACHED = 2;

    /**
     * Application process was killed by the activity manager because there were too many empty
     * processes; this would be set only when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_TOO_MANY_EMPTY = 3;

    /**
     * Application process was killed by the activity manager because there were too many cached
     * processes and this process had been in empty state for a long time;
     * this would be set only when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_TRIM_EMPTY = 4;

    /**
     * Application process was killed by the activity manager because system was on memory pressure
     * and this process took large amount of cached memory;
     * this would be set only when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_LARGE_CACHED = 5;

    /**
     * Application process was killed by the activity manager because the system was on low memory
     * pressure for a significant amount of time since last idle;
     * this would be set only when the reason is {@link #REASON_OTHER}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_MEMORY_PRESSURE = 6;

    /**
     * Application process was killed by the activity manager due to excessive CPU usage;
     * this would be set only when the reason is {@link #REASON_EXCESSIVE_RESOURCE_USAGE}.
     *
     * For internal use only.
     * @hide
     */
    public static final int SUBREASON_EXCESSIVE_CPU = 7;

    /**
     * @see {@link #getPid}
     */
    private int mPid;

    /**
     * @see {@link #getRealUid}
     */
    private int mRealUid;

    /**
     * @see {@link #getPackageUid}
     */
    private int mPackageUid;

    /**
     * @see {@link #getDefiningUid}
     */
    private int mDefiningUid;

    /**
     * @see {@link #getProcessName}
     */
    private String mProcessName;

    /**
     * @see {@link #getReason}
     */
    private @Reason int mReason;

    /**
     * @see {@link #getStatus}
     */
    private int mStatus;

    /**
     * @see {@link #getImportance}
     */
    private @Importance int mImportance;

    /**
     * @see {@link #getPss}
     */
    private long mPss;

    /**
     * @see {@link #getRss}
     */
    private long mRss;

    /**
     * @see {@link #getTimestamp}
     */
    private long mTimestamp;

    /**
     * @see {@link #getDescription}
     */
    private @Nullable String mDescription;

    /**
     * @see {@link #getSubReason}
     */
    private @SubReason int mSubReason;

    /**
     * @see {@link #getConnectionGroup}
     */
    private int mConnectionGroup;

    /**
     * @see {@link #getPackageName}
     */
    private String mPackageName;

    /**
     * @see {@link #getPackageList}
     */
    private String[] mPackageList;

    /** @hide */
    @IntDef(prefix = { "REASON_" }, value = {
        REASON_UNKNOWN,
        REASON_EXIT_SELF,
        REASON_SIGNALED,
        REASON_LOW_MEMORY,
        REASON_CRASH,
        REASON_CRASH_NATIVE,
        REASON_ANR,
        REASON_INITIALIZATION_FAILURE,
        REASON_PERMISSION_CHANGE,
        REASON_EXCESSIVE_RESOURCE_USAGE,
        REASON_OTHER,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Reason {}

    /** @hide */
    @IntDef(prefix = { "SUBREASON_" }, value = {
        SUBREASON_UNKNOWN,
        SUBREASON_WAIT_FOR_DEBUGGER,
        SUBREASON_TOO_MANY_CACHED,
        SUBREASON_TOO_MANY_EMPTY,
        SUBREASON_TRIM_EMPTY,
        SUBREASON_LARGE_CACHED,
        SUBREASON_MEMORY_PRESSURE,
        SUBREASON_EXCESSIVE_CPU,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SubReason {}

    /**
     * The process id of the process that died.
     */
    public int getPid() {
        return mPid;
    }

    /**
     * The kernel user identifier of the process, most of the time the system uses this
     * to do access control checks. It's typically the uid of the package where the component is
     * running from, except the case of isolated process, where this field identifies the kernel
     * user identifier that this process is actually running with, while the {@link #getPackageUid}
     * identifies the kernel user identifier that is assigned at the package installation time.
     */
    public int getRealUid() {
        return mRealUid;
    }

    /**
     * Similar to {@link #getRealUid}, it's the kernel user identifier that is assigned at the
     * package installation time.
     */
    public int getPackageUid() {
        return mPackageUid;
    }

    /**
     * Return the defining kernel user identifier, maybe different from {@link #getRealUid} and
     * {@link #getPackageUid}, if an external service was bound with the flag
     * {@link android.content.Context#BIND_EXTERNAL_SERVICE} - in this case, this field here
     * will be the kernel user identifier of the external service provider.
     */
    public int getDefiningUid() {
        return mDefiningUid;
    }

    /**
     * The actual process name it was running with.
     */
    public @NonNull String getProcessName() {
        return mProcessName;
    }

    /**
     * The reason code of the process's death.
     */
    public @Reason int getReason() {
        return mReason;
    }

    /*
     * The exit status argument of exit() if the application calls it, or the signal
     * number if the application is signaled.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * The importance of the process that it used to have before the death.
     */
    public @Importance int getImportance() {
        return mImportance;
    }

    /*
     * Last proportional set size of the memory that the process had used in kB.
     *
     * <p class="note">Note: This is the value from last sampling on the process,
     * it's NOT the exact memory information prior to its death; and it'll be zero
     * if the process died before system had a chance to take the sample. </p>
     */
    public long getPss() {
        return mPss;
    }

    /**
     * Last resident set size of the memory that the process had used in kB.
     *
     * <p class="note">Note: This is the value from last sampling on the process,
     * it's NOT the exact memory information prior to its death; and it'll be zero
     * if the process died before system had a chance to take the sample. </p>
     */
    public long getRss() {
        return mRss;
    }

    /**
     * The timestamp of the process's death, in milliseconds since the epoch,
     * as returned by {@link System#currentTimeMillis System.currentTimeMillis()}.
     */
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * The human readable description of the process's death, given by the system; could be null.
     *
     * <p class="note">Note: only intended to be human-readable and the system provides no
     * guarantees that the format is stable across devices or Android releases.</p>
     */
    public @Nullable String getDescription() {
        return mDescription;
    }

    /**
     * Return the user id of the record on a multi-user system.
     */
    public @NonNull UserHandle getUserHandle() {
        return UserHandle.of(UserHandle.getUserId(mRealUid));
    }

    /**
     * A subtype reason in conjunction with {@link #mReason}.
     *
     * For internal use only.
     *
     * @hide
     */
    public @SubReason int getSubReason() {
        return mSubReason;
    }

    /**
     * The connection group this process belongs to, if there is any.
     * @see {@link android.content.Context#updateServiceGroup}.
     *
     * For internal use only.
     *
     * @hide
     */
    public int getConnectionGroup() {
        return mConnectionGroup;
    }

    /**
     * Name of first package running in this process;
     *
     * For system internal use only, will not retain across processes.
     *
     * @hide
     */
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * List of packages running in this process;
     *
     * For system internal use only, will not retain across processes.
     *
     * @hide
     */
    public String[] getPackageList() {
        return mPackageList;
    }

    /**
     * @see {@link #getPid}
     *
     * @hide
     */
    public void setPid(final int pid) {
        mPid = pid;
    }

    /**
     * @see {@link #getRealUid}
     *
     * @hide
     */
    public void setRealUid(final int uid) {
        mRealUid = uid;
    }

    /**
     * @see {@link #getPackageUid}
     *
     * @hide
     */
    public void setPackageUid(final int uid) {
        mPackageUid = uid;
    }

    /**
     * @see {@link #getDefiningUid}
     *
     * @hide
     */
    public void setDefiningUid(final int uid) {
        mDefiningUid = uid;
    }

    /**
     * @see {@link #getProcessName}
     *
     * @hide
     */
    public void setProcessName(final String processName) {
        mProcessName = processName;
    }

    /**
     * @see {@link #getReason}
     *
     * @hide
     */
    public void setReason(final @Reason int reason) {
        mReason = reason;
    }

    /**
     * @see {@link #getStatus}
     *
     * @hide
     */
    public void setStatus(final int status) {
        mStatus = status;
    }

    /**
     * @see {@link #getImportance}
     *
     * @hide
     */
    public void setImportance(final @Importance int importance) {
        mImportance = importance;
    }

    /**
     * @see {@link #getPss}
     *
     * @hide
     */
    public void setPss(final long pss) {
        mPss = pss;
    }

    /**
     * @see {@link #getRss}
     *
     * @hide
     */
    public void setRss(final long rss) {
        mRss = rss;
    }

    /**
     * @see {@link #getTimestamp}
     *
     * @hide
     */
    public void setTimestamp(final long timestamp) {
        mTimestamp = timestamp;
    }

    /**
     * @see {@link #getDescription}
     *
     * @hide
     */
    public void setDescription(final String description) {
        mDescription = description;
    }

    /**
     * @see {@link #getSubReason}
     *
     * @hide
     */
    public void setSubReason(final @SubReason int subReason) {
        mSubReason = subReason;
    }

    /**
     * @see {@link #getConnectionGroup}
     *
     * @hide
     */
    public void setConnectionGroup(final int connectionGroup) {
        mConnectionGroup = connectionGroup;
    }

    /**
     * @see {@link #getPackageName}
     *
     * @hide
     */
    public void setPackageName(final String packageName) {
        mPackageName = packageName;
    }

    /**
     * @see {@link #getPackageList}
     *
     * @hide
     */
    public void setPackageList(final String[] packageList) {
        mPackageList = packageList;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPid);
        dest.writeInt(mRealUid);
        dest.writeInt(mPackageUid);
        dest.writeInt(mDefiningUid);
        dest.writeString(mProcessName);
        dest.writeInt(mConnectionGroup);
        dest.writeInt(mReason);
        dest.writeInt(mSubReason);
        dest.writeInt(mStatus);
        dest.writeInt(mImportance);
        dest.writeLong(mPss);
        dest.writeLong(mRss);
        dest.writeLong(mTimestamp);
        dest.writeString(mDescription);
    }

    /** @hide */
    public ApplicationExitInfo() {
    }

    /** @hide */
    public ApplicationExitInfo(ApplicationExitInfo other) {
        mPid = other.mPid;
        mRealUid = other.mRealUid;
        mPackageUid = other.mPackageUid;
        mDefiningUid = other.mDefiningUid;
        mProcessName = other.mProcessName;
        mConnectionGroup = other.mConnectionGroup;
        mReason = other.mReason;
        mStatus = other.mStatus;
        mSubReason = other.mSubReason;
        mImportance = other.mImportance;
        mPss = other.mPss;
        mRss = other.mRss;
        mTimestamp = other.mTimestamp;
        mDescription = other.mDescription;
    }

    private ApplicationExitInfo(@NonNull Parcel in) {
        mPid = in.readInt();
        mRealUid = in.readInt();
        mPackageUid = in.readInt();
        mDefiningUid = in.readInt();
        mProcessName = in.readString();
        mConnectionGroup = in.readInt();
        mReason = in.readInt();
        mSubReason = in.readInt();
        mStatus = in.readInt();
        mImportance = in.readInt();
        mPss = in.readLong();
        mRss = in.readLong();
        mTimestamp = in.readLong();
        mDescription = in.readString();
    }

    public @NonNull static final Creator<ApplicationExitInfo> CREATOR =
            new Creator<ApplicationExitInfo>() {
        @Override
        public ApplicationExitInfo createFromParcel(Parcel in) {
            return new ApplicationExitInfo(in);
        }

        @Override
        public ApplicationExitInfo[] newArray(int size) {
            return new ApplicationExitInfo[size];
        }
    };

    /** @hide */
    public void dump(@NonNull PrintWriter pw, @Nullable String prefix, @Nullable String seqSuffix,
            @NonNull SimpleDateFormat sdf) {
        pw.println(prefix + "ApplicationExitInfo " + seqSuffix + ":");
        pw.println(prefix + "  timestamp=" + sdf.format(new Date(mTimestamp)));
        pw.println(prefix + "  pid=" + mPid);
        pw.println(prefix + "  realUid=" + mRealUid);
        pw.println(prefix + "  packageUid=" + mPackageUid);
        pw.println(prefix + "  definingUid=" + mDefiningUid);
        pw.println(prefix + "  user=" + UserHandle.getUserId(mPackageUid));
        pw.println(prefix + "  process=" + mProcessName);
        pw.println(prefix + "  reason=" + mReason + " (" + reasonCodeToString(mReason) + ")");
        pw.println(prefix + "  status=" + mStatus);
        pw.println(prefix + "  importance=" + mImportance);
        pw.print(prefix + "  pss="); DebugUtils.printSizeValue(pw, mPss << 10); pw.println();
        pw.print(prefix + "  rss="); DebugUtils.printSizeValue(pw, mRss << 10); pw.println();
        pw.println(prefix + "  description=" + mDescription);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ApplicationExitInfo(timestamp=");
        sb.append(new SimpleDateFormat().format(new Date(mTimestamp)));
        sb.append(" pid=").append(mPid);
        sb.append(" realUid=").append(mRealUid);
        sb.append(" packageUid=").append(mPackageUid);
        sb.append(" definingUid=").append(mDefiningUid);
        sb.append(" user=").append(UserHandle.getUserId(mPackageUid));
        sb.append(" process=").append(mProcessName);
        sb.append(" reason=").append(mReason).append(" (")
                .append(reasonCodeToString(mReason)).append(")");
        sb.append(" status=").append(mStatus);
        sb.append(" importance=").append(mImportance);
        sb.append(" pss="); DebugUtils.sizeValueToString(mPss << 10, sb);
        sb.append(" rss="); DebugUtils.sizeValueToString(mRss << 10, sb);
        sb.append(" description=").append(mDescription);
        return sb.toString();
    }

    private static String reasonCodeToString(@Reason int reason) {
        switch (reason) {
            case REASON_EXIT_SELF:
                return "EXIT_SELF";
            case REASON_SIGNALED:
                return "SIGNALED";
            case REASON_LOW_MEMORY:
                return "LOW_MEMORY";
            case REASON_CRASH:
                return "APP CRASH(EXCEPTION)";
            case REASON_CRASH_NATIVE:
                return "APP CRASH(NATIVE)";
            case REASON_ANR:
                return "ANR";
            case REASON_INITIALIZATION_FAILURE:
                return "INITIALIZATION FAILURE";
            case REASON_PERMISSION_CHANGE:
                return "PERMISSION CHANGE";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "EXCESSIVE RESOURCE USAGE";
            case REASON_OTHER:
                return "OTHER KILLS BY SYSTEM";
            default:
                return "UNKNOWN";
        }
    }

    /** @hide */
    public static String subreasonToString(@SubReason int subreason) {
        switch (subreason) {
            case SUBREASON_WAIT_FOR_DEBUGGER:
                return "WAIT FOR DEBUGGER";
            case SUBREASON_TOO_MANY_CACHED:
                return "TOO MANY CACHED PROCS";
            case SUBREASON_TOO_MANY_EMPTY:
                return "TOO MANY EMPTY PROCS";
            case SUBREASON_TRIM_EMPTY:
                return "TRIM EMPTY";
            case SUBREASON_LARGE_CACHED:
                return "LARGE CACHED";
            case SUBREASON_MEMORY_PRESSURE:
                return "MEMORY PRESSURE";
            case SUBREASON_EXCESSIVE_CPU:
                return "EXCESSIVE CPU USAGE";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Write to a protocol buffer output stream.
     * Protocol buffer message definition at {@link android.app.ApplicationExitInfoProto}
     *
     * @param proto    Stream to write the ApplicationExitInfo object to.
     * @param fieldId  Field Id of the ApplicationExitInfo as defined in the parent message
     * @hide
     */
    public void writeToProto(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        proto.write(ApplicationExitInfoProto.PID, mPid);
        proto.write(ApplicationExitInfoProto.REAL_UID, mRealUid);
        proto.write(ApplicationExitInfoProto.PACKAGE_UID, mPackageUid);
        proto.write(ApplicationExitInfoProto.DEFINING_UID, mDefiningUid);
        proto.write(ApplicationExitInfoProto.PROCESS_NAME, mProcessName);
        proto.write(ApplicationExitInfoProto.CONNECTION_GROUP, mConnectionGroup);
        proto.write(ApplicationExitInfoProto.REASON, mReason);
        proto.write(ApplicationExitInfoProto.SUB_REASON, mSubReason);
        proto.write(ApplicationExitInfoProto.STATUS, mStatus);
        proto.write(ApplicationExitInfoProto.IMPORTANCE, mImportance);
        proto.write(ApplicationExitInfoProto.PSS, mPss);
        proto.write(ApplicationExitInfoProto.RSS, mRss);
        proto.write(ApplicationExitInfoProto.TIMESTAMP, mTimestamp);
        proto.write(ApplicationExitInfoProto.DESCRIPTION, mDescription);
        proto.end(token);
    }

    /**
     * Read from a protocol buffer input stream.
     * Protocol buffer message definition at {@link android.app.ApplicationExitInfoProto}
     *
     * @param proto   Stream to read the ApplicationExitInfo object from.
     * @param fieldId Field Id of the ApplicationExitInfo as defined in the parent message
     * @hide
     */
    public void readFromProto(ProtoInputStream proto, long fieldId)
            throws IOException, WireTypeMismatchException {
        final long token = proto.start(fieldId);
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case (int) ApplicationExitInfoProto.PID:
                    mPid = proto.readInt(ApplicationExitInfoProto.PID);
                    break;
                case (int) ApplicationExitInfoProto.REAL_UID:
                    mRealUid = proto.readInt(ApplicationExitInfoProto.REAL_UID);
                    break;
                case (int) ApplicationExitInfoProto.PACKAGE_UID:
                    mPackageUid = proto.readInt(ApplicationExitInfoProto.PACKAGE_UID);
                    break;
                case (int) ApplicationExitInfoProto.DEFINING_UID:
                    mDefiningUid = proto.readInt(ApplicationExitInfoProto.DEFINING_UID);
                    break;
                case (int) ApplicationExitInfoProto.PROCESS_NAME:
                    mProcessName = proto.readString(ApplicationExitInfoProto.PROCESS_NAME);
                    break;
                case (int) ApplicationExitInfoProto.CONNECTION_GROUP:
                    mConnectionGroup = proto.readInt(ApplicationExitInfoProto.CONNECTION_GROUP);
                    break;
                case (int) ApplicationExitInfoProto.REASON:
                    mReason = proto.readInt(ApplicationExitInfoProto.REASON);
                    break;
                case (int) ApplicationExitInfoProto.SUB_REASON:
                    mSubReason = proto.readInt(ApplicationExitInfoProto.SUB_REASON);
                    break;
                case (int) ApplicationExitInfoProto.STATUS:
                    mStatus = proto.readInt(ApplicationExitInfoProto.STATUS);
                    break;
                case (int) ApplicationExitInfoProto.IMPORTANCE:
                    mImportance = proto.readInt(ApplicationExitInfoProto.IMPORTANCE);
                    break;
                case (int) ApplicationExitInfoProto.PSS:
                    mPss = proto.readLong(ApplicationExitInfoProto.PSS);
                    break;
                case (int) ApplicationExitInfoProto.RSS:
                    mRss = proto.readLong(ApplicationExitInfoProto.RSS);
                    break;
                case (int) ApplicationExitInfoProto.TIMESTAMP:
                    mTimestamp = proto.readLong(ApplicationExitInfoProto.TIMESTAMP);
                    break;
                case (int) ApplicationExitInfoProto.DESCRIPTION:
                    mDescription = proto.readString(ApplicationExitInfoProto.DESCRIPTION);
                    break;
            }
        }
        proto.end(token);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof ApplicationExitInfo)) {
            return false;
        }
        ApplicationExitInfo o = (ApplicationExitInfo) other;
        return mPid == o.mPid && mRealUid == o.mRealUid && mPackageUid == o.mPackageUid
                && mDefiningUid == o.mDefiningUid
                && mConnectionGroup == o.mConnectionGroup && mReason == o.mReason
                && mSubReason == o.mSubReason && mImportance == o.mImportance
                && mStatus == o.mStatus && mTimestamp == o.mTimestamp
                && mPss == o.mPss && mRss == o.mRss
                && TextUtils.equals(mProcessName, o.mProcessName)
                && TextUtils.equals(mDescription, o.mDescription);
    }

    @Override
    public int hashCode() {
        int result = mPid;
        result = 31 * result + mRealUid;
        result = 31 * result + mPackageUid;
        result = 31 * result + mDefiningUid;
        result = 31 * result + mConnectionGroup;
        result = 31 * result + mReason;
        result = 31 * result + mSubReason;
        result = 31 * result + mImportance;
        result = 31 * result + mStatus;
        result = 31 * result + (int) mPss;
        result = 31 * result + (int) mRss;
        result = 31 * result + Long.hashCode(mTimestamp);
        result = 31 * result + Objects.hashCode(mProcessName);
        result = 31 * result + Objects.hashCode(mDescription);
        return result;
    }
}
