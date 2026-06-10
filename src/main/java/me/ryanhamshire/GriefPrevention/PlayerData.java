/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import com.griefprevention.visualization.BoundaryVisualization;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

//holds all of GriefPrevention's player-tied data
public class PlayerData
{
    //the player's ID
    public UUID playerID;

    //the player's claims
    private Vector<Claim> claims = null;

    //what "mode" the shovel is in determines what it will do when it's used
    public ShovelMode shovelMode = ShovelMode.Basic;

    //last place the player used the shovel, useful in creating and resizing claims,
    //because the player must use the shovel twice in those instances
    public Location lastShovelLocation = null;

    //the claim this player is currently resizing
    public Claim claimResizing = null;

    //the claim this player is currently subdividing
    public Claim claimSubdividing = null;

    //whether or not the player has a pending /trapped rescue
    public boolean pendingTrapped = false;

    //whether this player was recently warned about building outside land claims
    boolean warnedAboutBuildingOutsideClaims = false;

    //whether the player was kicked (set and used during logout)
    boolean wasKicked = false;

    //visualization
    private transient @Nullable BoundaryVisualization visibleBoundaries = null;

    /** @deprecated Use {@link #getVisibleBoundaries} and {@link #setVisibleBoundaries(BoundaryVisualization)} */
    @Deprecated(forRemoval = true, since = "16.18")
    public Visualization currentVisualization = null;

    //anti-camping pvp protection
    public boolean pvpImmune = false;
    public long lastSpawn = 0;

    //ignore claims mode
    public boolean ignoreClaims = false;

    //the last claim this player was in, that we know of
    public Claim lastClaim = null;

    //pvp
    public long lastPvpTimestamp = 0;
    public String lastPvpPlayer = "";

    //safety confirmation for deleting multi-subdivision claims
    public boolean warnedAboutMajorDeletion = false;

    public InetAddress ipAddress;

    //whether or not this player has received a message about unlocking death drops since his last death
    boolean receivedDropUnlockAdvertisement = false;

    //whether or not this player's dropped items (on death) are unlocked for other players to pick up
    boolean dropsAreUnlocked = false;

    //message to send to player after he respawns
    String messageOnRespawn = null;

    //timestamp for last "you're building outside your land claims" message
    Long buildWarningTimestamp = null;

    //timestamp for last warning when placing TNT on explosion protected claim
    Long explosivesWarningTimestamp = null;

    //spot where a player can't talk, used to mute new players until they've moved a little
    //this is an anti-bot strategy.
    Location noChatLocation = null;

    //ignore list
    //true means invisible (admin-forced ignore), false means player-created ignore
    public ConcurrentHashMap<UUID, Boolean> ignoredPlayers = new ConcurrentHashMap<>();
    public boolean ignoreListChanged = false;

    //profanity warning, once per play session
    boolean profanityWarned = false;

    //whether or not this player is "in" pvp combat
    public boolean inPvpCombat()
    {
        if (this.lastPvpTimestamp == 0) return false;

        long now = Calendar.getInstance().getTimeInMillis();

        long elapsed = now - this.lastPvpTimestamp;

        if (elapsed > GriefPrevention.instance.config_pvp_combatTimeoutSeconds * 1000) //X seconds
        {
            this.lastPvpTimestamp = 0;
            return false;
        }

        return true;
    }

    // Claim blocks are not used; claims are unlimited.
    public int getRemainingClaimBlocks()
    {
        return Integer.MAX_VALUE;
    }

    public int getAccruedClaimBlocks()
    {
        return 0;
    }

    public void setAccruedClaimBlocks(Integer accruedClaimBlocks) {}

    public int getBonusClaimBlocks()
    {
        return 0;
    }

    public void setBonusClaimBlocks(Integer bonusClaimBlocks) {}

    public Vector<Claim> getClaims()
    {
        if (this.claims == null)
        {
            this.claims = new Vector<>();

            DataStore dataStore = GriefPrevention.instance.dataStore;
            for (int i = 0; i < dataStore.claims.size(); i++)
            {
                Claim claim = dataStore.claims.get(i);
                if (!claim.inDataStore)
                {
                    Claim remove = dataStore.claims.remove(i--);
                    dataStore.claimIDMap.remove(remove.getID());
                    for (Claim child : remove.children)
                    {
                        dataStore.claimIDMap.remove(child.getID());
                    }
                    continue;
                }
                if (playerID.equals(claim.ownerID))
                {
                    this.claims.add(claim);
                    dataStore.claimIDMap.put(claim.getID(), claim);
                    for (Claim child : claim.children)
                    {
                        dataStore.claimIDMap.put(child.getID(), child);
                    }
                }
            }
        }

        for (int i = 0; i < this.claims.size(); i++)
        {
            if (!claims.get(i).inDataStore)
            {
                claims.remove(i--);
            }
        }

        return claims;
    }

    public @Nullable BoundaryVisualization getVisibleBoundaries()
    {
        return visibleBoundaries;
    }

    public void setVisibleBoundaries(@Nullable BoundaryVisualization visibleBoundaries)
    {
        if (this.visibleBoundaries != null) {
            this.visibleBoundaries.revert(Bukkit.getPlayer(playerID));
        }

        this.visibleBoundaries = visibleBoundaries;
    }

}
