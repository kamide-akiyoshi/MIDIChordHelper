package camidion.chordhelper.chordmatrix;

import java.util.List;

class NoteWeight {
	private int weight = 0;
	private int bassWeight = 0;
	private boolean isActive = false;
	private boolean isBassActive = false;
	private List<ChordLabelMarker> markers;
	public NoteWeight(List<ChordLabelMarker> markers) { this.markers = markers; }
	void add(int weightDiff) {
		if( (weight += weightDiff) < 0 ) weight = 0;
		if( (weight > 0) != isActive ) {
			isActive = !isActive;
			for(ChordLabelMarker m : markers) m.mark(isActive);
		}
	}
	void addBass(int weightDiff) {
		if( (bassWeight += weightDiff) < 0 ) bassWeight = 0;
		if( (bassWeight > 0) != isBassActive ) {
			isBassActive = !isBassActive;
			for(ChordLabelMarker m : markers) if(!m.markBass(isBassActive)) break;
		}
		add(weightDiff);
	}
	void clear() {
		weight = bassWeight = 0;
		isActive = isBassActive = false;
		for(ChordLabelMarker m : markers) { m.mark(false); m.markBass(false); }
	}
}