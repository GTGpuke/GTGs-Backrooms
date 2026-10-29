// Calendrier des neons, jumeau exact de NeonSchedule (Java) : entiers sur 32 bits (multiplications modulo 2^32,
// decalages non signes, ou exclusif), aucun flottant dans la decision. Les deux sont valides contre les memes vecteurs.

uint gtgs_mix(uint value) {
    uint z = value;
    z = (z ^ (z >> 16u)) * 0x7FEB352Du;
    z = (z ^ (z >> 15u)) * 0x846CA68Bu;
    return z ^ (z >> 16u);
}

uint gtgs_neonCharacter(ivec3 p, int seed) {
    return gtgs_mix(uint(p.x) * 0x9E3779B1u ^ gtgs_mix(uint(p.y) * 0x85EBCA77u ^ gtgs_mix(uint(p.z) * 0xC2B2AE3Du ^ uint(seed))));
}

// 0 : brille sans faillir, 1 : vacille, 2 : mort.
int gtgs_neonKind(uint character, float flickering, float dead) {
    int lot = int(character >> 22u);
    if (lot < int(flickering * 1024.0)) {
        return 1;
    }
    if (lot < int((flickering + dead) * 1024.0)) {
        return 2;
    }
    return 0;
}

// Coupure par creneaux de quatre ticks, une fois sur huit en moyenne.
bool gtgs_neonFlickers(uint character, int tick) {
    uint slot = uint(tick) >> 2u;
    return (gtgs_mix(character ^ slot * 0x27D4EB2Fu) >> 29u) == 0u;
}

float gtgs_neonIntensity(ivec3 p, int seed, int tick, float flickering, float dead) {
    uint character = gtgs_neonCharacter(p, seed);
    int kind = gtgs_neonKind(character, flickering, dead);
    if (kind == 1) {
        return gtgs_neonFlickers(character, tick) ? 0.12 : 1.0;
    }
    return kind == 2 ? 0.12 : 1.0;
}
