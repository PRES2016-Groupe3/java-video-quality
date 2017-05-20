package upmc.master.reseaux;

public enum Camera {
	GO_PRO("GoPro"), AR_DRONE("AR.Drone 1.0");
	private String name;

	private Camera(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

}
