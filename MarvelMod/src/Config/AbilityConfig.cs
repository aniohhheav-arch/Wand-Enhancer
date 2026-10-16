using System.Collections.Generic;

namespace MarvelMod.Config;

/// <summary>Schema for a single ability's tunable parameters.</summary>
public class AbilityConfig
{
    public bool   Enabled        { get; set; } = true;
    public float  Cooldown       { get; set; } = 10f;
    public float  Duration       { get; set; } = 3f;
    public float  CastTime       { get; set; } = 0f;
    public float  Range          { get; set; } = 5f;
    public float  Radius         { get; set; } = 3f;
    public float  Damage         { get; set; } = 0f;
    public int    MaxTargets     { get; set; } = 1;
    public float  VfxIntensity   { get; set; } = 1f;
    public float  CinematicDur   { get; set; } = 3f;
    public bool   PracticeOnly   { get; set; } = false;
    /// <summary>Meeting abilities: uses per meeting.</summary>
    public int    UsesPerMeeting { get; set; } = 1;
    /// <summary>Meeting abilities: discussion, voting or both.</summary>
    public string MeetingPhase   { get; set; } = "both";
}
