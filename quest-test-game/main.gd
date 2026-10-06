extends Node3D

var xr_origin: XROrigin3D
var camera: XRCamera3D
var left_hand: XRController3D
var right_hand: XRController3D
var menu: Node3D
var menu_panel: MeshInstance3D
var menu_text: Label3D
var features = {"Platforms": false, "Flying": false, "Super Jump": false, "Long Arms": false}
var gravity := 9.8
var vertical_velocity := 0.0
var normal_jump := 3.5
var super_jump := 7.0
var fly_speed := 2.5

func _ready():
    var openxr = XRServer.find_interface("OpenXR")
    if openxr:
        openxr.initialize()
    build_world()
    build_xr_rig()
    build_menu()

func build_world():
    var env = WorldEnvironment.new()
    var environment = Environment.new()
    environment.background_mode = Environment.BG_COLOR
    environment.background_color = Color(0.035, 0.045, 0.07)
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    environment.ambient_light_color = Color(0.7, 0.8, 1.0)
    environment.ambient_light_energy = 1.2
    env.environment = environment
    add_child(env)

    var light = DirectionalLight3D.new()
    light.rotation_degrees = Vector3(-45, -25, 0)
    light.light_energy = 1.2
    add_child(light)

    # A few small test platforms; there is deliberately no giant base plate.
    for p in [Vector3(0, -1.0, -2), Vector3(1.8, 0.2, -3), Vector3(-1.8, 1.0, -4)]:
        spawn_platform(p)

func build_xr_rig():
    xr_origin = XROrigin3D.new()
    xr_origin.name = "XROrigin3D"
    add_child(xr_origin)

    camera = XRCamera3D.new()
    camera.name = "XRCamera3D"
    xr_origin.add_child(camera)

    left_hand = XRController3D.new()
    left_hand.name = "LeftHand"
    left_hand.tracker = &"left_hand"
    xr_origin.add_child(left_hand)
    left_hand.button_pressed.connect(_controller_button)

    right_hand = XRController3D.new()
    right_hand.name = "RightHand"
    right_hand.tracker = &"right_hand"
    xr_origin.add_child(right_hand)
    right_hand.button_pressed.connect(_controller_button)

func build_menu():
    menu = Node3D.new()
    menu.name = "QuestTestMenu"
    menu.position = Vector3(-0.28, -0.02, -0.55)
    menu.visible = false
    camera.add_child(menu)

    var panel = QuadMesh.new()
    panel.size = Vector2(0.42, 0.48)
    menu_panel = MeshInstance3D.new()
    menu_panel.mesh = panel
    menu_panel.position = Vector3(0, 0, 0)
    var mat = StandardMaterial3D.new()
    mat.albedo_color = Color(0.035, 0.04, 0.06, 0.96)
    mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    menu_panel.material_override = mat
    menu.add_child(menu_panel)

    menu_text = Label3D.new()
    menu_text.font_size = 32
    menu_text.modulate = Color(0.9, 0.95, 1.0)
    menu_text.pixel_size = 0.0018
    menu_text.position = Vector3(-0.19, 0.20, -0.01)
    menu_text.text = ""
    menu.add_child(menu_text)
    refresh_menu()

func refresh_menu():
    if not menu_text:
        return
    var s = "QUEST TEST MENU\n\n"
    for key in features.keys():
        s += ("ON  " if features[key] else "OFF ") + key + "\n"
    s += "\nY = CLOSE / OPEN\nTrigger = toggle aimed item"
    menu_text.text = s

func _controller_button(button_name: String):
    if button_name in ["y_button", "menu_button"]:
        menu.visible = not menu.visible
        return
    if not menu.visible:
        return
    if button_name in ["trigger_click", "trigger"]:
        # Simple cycling toggle: trigger while menu is open toggles the next feature.
        var keys = features.keys()
        for key in keys:
            if not features[key]:
                features[key] = true
                break
        refresh_menu()

func _process(delta):
    if not xr_origin:
        return

    # Flying: hold the right controller trigger to move where the headset faces.
    if features["Flying"] and right_hand and right_hand.is_button_pressed("trigger_click"):
        var dir = -camera.global_transform.basis.z
        dir.y = 0
        if dir.length() > 0.01:
            dir = dir.normalized()
            xr_origin.global_position += dir * fly_speed * delta

    # Basic gravity/jump simulation for the test rig.
    if not features["Flying"]:
        vertical_velocity -= gravity * delta
        xr_origin.position.y += vertical_velocity * delta
        if xr_origin.position.y < 0:
            xr_origin.position.y = 0
            vertical_velocity = 0

    # Super jump: A/X button launches the test rig upward.
    if (right_hand and right_hand.is_button_pressed("a_button")) or (left_hand and left_hand.is_button_pressed("x_button")):
        if abs(vertical_velocity) < 0.05:
            vertical_velocity = super_jump if features["Super Jump"] else normal_jump

    # Long arms is represented by extending the virtual hand marker position.
    update_hand_markers()

func update_hand_markers():
    for hand in [left_hand, right_hand]:
        if not hand:
            continue
        var marker = hand.get_node_or_null("LongArmMarker")
        if not marker:
            marker = MeshInstance3D.new()
            marker.name = "LongArmMarker"
            var sphere = SphereMesh.new()
            sphere.radius = 0.035
            sphere.height = 0.07
            marker.mesh = sphere
            hand.add_child(marker)
        marker.position = Vector3(0, 0, -0.35 if features["Long Arms"] else -0.08)

func spawn_platform(pos: Vector3):
    var body = StaticBody3D.new()
    body.position = pos
    var mesh = MeshInstance3D.new()
    var box = BoxMesh.new()
    box.size = Vector3(1.4, 0.12, 1.4)
    mesh.mesh = box
    body.add_child(mesh)
    var collider = CollisionShape3D.new()
    var shape = BoxShape3D.new()
    shape.size = Vector3(1.4, 0.12, 1.4)
    collider.shape = shape
    body.add_child(collider)
    add_child(body)

func spawn_hand_platform(hand: XRController3D):
    if not features["Platforms"] or not hand:
        return
    var p = hand.global_position + (-hand.global_transform.basis.z * 1.0)
    spawn_platform(p)
