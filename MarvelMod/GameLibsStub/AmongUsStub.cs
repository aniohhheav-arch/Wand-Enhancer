using System.Collections.Generic;

// Among Us types. Compiled into Assembly-CSharp alongside UnityEngine types.

public class PlayerControl : UnityEngine.MonoBehaviour
{
    public static PlayerControl LocalPlayer { get; set; } = null!;
    public static List<PlayerControl> AllPlayerControls { get; } = new();
    public byte PlayerId { get; set; }
    public bool moveable { get; set; } = true;
    public GameData.PlayerInfo? Data { get; set; }
    public CustomNetworkTransform NetTransform { get; } = null!;
    public void UpdateFromPlayerData(GameData.PlayerInfo data, PlayerOutfitType type, bool s, bool b) { }
}

public class CustomNetworkTransform : UnityEngine.MonoBehaviour
{
    public void RpcSnapTo(UnityEngine.Vector2 pos) { }
}

public class GameData
{
    public class PlayerInfo
    {
        public string PlayerName { get; set; } = "";
        public bool   Disconnected { get; set; }
        public Dictionary<PlayerOutfitType, PlayerOutfit> Outfits { get; } = new();
        public RoleTypes Role { get; }
    }
}

public class PlayerOutfit
{
    public string HatId   { get; set; } = "";
    public string SkinId  { get; set; } = "";
    public int    ColorId { get; set; }
}

public enum PlayerOutfitType { Default }
public enum RoleTypes { Crewmate, Impostor }

public class HudManager : UnityEngine.MonoBehaviour
{
    public static HudManager? Instance { get; set; }
    public PlayerNotify? Notifier { get; }
}

public class PlayerNotify : UnityEngine.MonoBehaviour
{
    public void AddItem(string text) { }
}
