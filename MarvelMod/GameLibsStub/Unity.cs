using System;
using System.Collections;

namespace UnityEngine
{
    public class Object : global::Il2CppInterop.Runtime.InteropTypes.Il2CppObjectBase
    {
        public Object() : base(IntPtr.Zero) { }
        public string name { get; set; } = "";
        public static void Destroy(Object obj) { }
        public static void Destroy(Object obj, float t) { }
        public static T? FindObjectOfType<T>() where T : Object => null;
        public static T[] FindObjectsOfType<T>() where T : Object => Array.Empty<T>();
    }
    public class Component : Object
    {
        public Transform transform { get; } = null!;
        public GameObject gameObject { get; } = null!;
        public T GetComponent<T>() => default!;
        public T AddComponent<T>() => default!;
        public void StartCoroutine(IEnumerator routine) { }
    }
    public class MonoBehaviour : Component { }
    public class GameObject : Object
    {
        public GameObject() { }
        public GameObject(string name) { Name = name; }
        public string Name { get; set; } = "";
        public bool activeSelf { get; set; } = true;
        public Transform transform { get; } = null!;
        public T AddComponent<T>() => default!;
        public T GetComponent<T>() => default!;
    }
    public class Transform : Component
    {
        public Vector3 position { get; set; }
        public Vector3 localPosition { get; set; }
        public Vector3 localScale { get; set; } = Vector3.one;
    }
    public class SpriteRenderer : Component
    {
        public Color color { get; set; }
        public int sortingOrder { get; set; }
    }
    public class ParticleSystem : Component
    {
        public MainModule main => default;
        public EmissionModule emission => default;
        public struct MainModule
        {
            public float startLifetime { get; set; }
            public float startSpeed    { get; set; }
            public float startSize     { get; set; }
            public Color startColor    { get; set; }
            public int   maxParticles  { get; set; }
        }
        public struct EmissionModule
        {
            public float rateOverTime { get; set; }
            public void SetBursts(Burst[] bursts) { }
        }
        public struct Burst
        {
            public Burst(float t, short c) { }
        }
    }
    public class Camera : Component
    {
        public static Camera? main { get; }
    }
    public class Physics2D
    {
        public static RaycastHit2D Raycast(Vector3 origin, Vector2 dir, float dist, int mask) => default;
    }
    public struct RaycastHit2D
    {
        public Collider2D collider { get; }
    }
    public class Collider2D : Component { }
    public class LayerMask
    {
        public static int GetMask(params string[] names) => 0;
    }
    public struct Vector2
    {
        public float x, y;
        public Vector2(float x, float y) { this.x = x; this.y = y; }
        public static Vector2 zero    => new(0,0);
        public static Vector2 right   => new(1,0);
        public Vector2 normalized { get { float m = MathF.Sqrt(x*x+y*y); return m>0?new(x/m,y/m):zero; } }
        public static float Distance(Vector2 a, Vector2 b) => MathF.Sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y));
        public static implicit operator Vector3(Vector2 v) => new(v.x, v.y, 0);
        public static implicit operator Vector2(Vector3 v) => new(v.x, v.y);
        public static bool operator ==(Vector2 a, Vector2 b) => a.x == b.x && a.y == b.y;
        public static bool operator !=(Vector2 a, Vector2 b) => !(a == b);
        public static Vector2 operator *(Vector2 v, float f) => new(v.x * f, v.y * f);
        public static Vector2 operator -(Vector2 a, Vector2 b) => new(a.x - b.x, a.y - b.y);
        public static Vector2 operator +(Vector2 a, Vector2 b) => new(a.x + b.x, a.y + b.y);
        public override bool Equals(object? obj) => obj is Vector2 v && this == v;
        public override int GetHashCode() => HashCode.Combine(x, y);
    }
    public struct Vector3
    {
        public float x, y, z;
        public Vector3(float x, float y, float z) { this.x=x; this.y=y; this.z=z; }
        public static Vector3 zero => new(0,0,0);
        public static Vector3 one  => new(1,1,1);
        public static Vector3 right=> new(1,0,0);
        public static Vector3 MoveTowards(Vector3 c, Vector3 t, float d) => c;
        public static float Distance(Vector3 a, Vector3 b) => MathF.Sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y));
        public static Vector3 operator+(Vector3 a, Vector3 b) => new(a.x+b.x,a.y+b.y,a.z+b.z);
        public static Vector3 operator*(Vector3 a, float f) => new(a.x*f,a.y*f,a.z*f);
        public static Vector3 operator -(Vector3 a, Vector3 b) => new(a.x-b.x, a.y-b.y, a.z-b.z);
    }
    public struct Color
    {
        public float r, g, b, a;
        public Color(float r, float g, float b, float a=1f){this.r=r;this.g=g;this.b=b;this.a=a;}
    }
    public class Random
    {
        public static float Range(float min, float max) => min;
        public static int   Range(int min, int max)     => min;
        public static Vector2 insideUnitCircle          => Vector2.zero;
    }
    public class Mathf
    {
        public static float Max(float a, float b) => MathF.Max(a,b);
        public static float Min(float a, float b) => MathF.Min(a,b);
        public static float Sin(float v)          => MathF.Sin(v);
        public static float Sqrt(float v)         => MathF.Sqrt(v);
        public static int   RoundToInt(float v)   => (int)MathF.Round(v);
        public static float Clamp(float v, float a, float b) => Math.Clamp(v,a,b);
    }
    public class Time
    {
        public static float realtimeSinceStartup { get; }
        public static float deltaTime            { get; }
    }
    public class Input
    {
        public static float GetAxisRaw(string axis) => 0f;
        public static bool  GetKeyDown(KeyCode key) => false;
    }
    public enum KeyCode { F6, F7, F8, F9, F10, Return }
    public class WaitForSeconds : IEnumerator
    {
        public WaitForSeconds(float t) { }
        public object? Current => null;
        public bool MoveNext() => false;
        public void Reset() { }
    }
    public class GUI
    {
        public static void Box(Rect r, string t) { }
        public static string TextField(Rect r, string t) => t;
        public static bool  Button(Rect r, string t) => false;
        public static void  Label(Rect r, string t) { }
        public static Vector2 BeginScrollView(Rect r, Vector2 s, Rect vr) => s;
        public static void  EndScrollView() { }
        public static void  SetNextControlName(string n) { }
    }
    public struct Rect
    {
        public float x, y, width, height;
        public Rect(float x, float y, float w, float h){this.x=x;this.y=y;width=w;height=h;}
        public float xMax => x + width;
        public float yMax => y + height;
    }
    public class Screen { public static int width { get; } public static int height { get; } }
    public class Event
    {
        public static Event? current { get; }
        public EventType type { get; }
        public KeyCode   keyCode { get; }
    }
    public enum EventType { KeyDown, KeyUp, Layout, Repaint }
}
