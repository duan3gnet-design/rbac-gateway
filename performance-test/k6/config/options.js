/**
 * k6 shared option presets.
 * Import vào từng test script theo nhu cầu.
 */

/** Smoke test — kiểm tra system không lỗi cơ bản (1 VU, 1 phút) */
export const SMOKE = {
  vus: 1,
  duration: '1m',
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

/** Load test — traffic bình thường, ramp-up rồi sustain */
export const LOAD = {
  stages: [
    { duration: '1m',  target: 20 },   // ramp-up
    { duration: '3m',  target: 20 },   // sustain
    { duration: '30s', target: 0  },   // ramp-down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
  },
};

/** Stress test — tìm điểm gãy của hệ thống */
export const STRESS = {
  stages: [
    { duration: '1m',  target: 50  },
    { duration: '2m',  target: 100 },
    { duration: '2m',  target: 200 },
    { duration: '1m',  target: 0   },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
  },
};

/** Soak test — chạy dài để detect memory leak */
export const SOAK = {
  stages: [
    { duration: '2m',  target: 20 },   // ramp-up
    { duration: '30m', target: 20 },   // sustain dài
    { duration: '2m',  target: 0  },   // ramp-down
  ],
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

/** Spike test — kiểm tra Circuit Breaker và Rate Limit chịu đựng burst */
export const SPIKE = {
  stages: [
    { duration: '30s', target: 5   },   // baseline
    { duration: '15s', target: 300 },   // spike đột ngột
    { duration: '30s', target: 300 },   // sustain spike
    { duration: '15s', target: 5   },   // recovery
    { duration: '30s', target: 5   },   // verify recovery
  ],
  thresholds: {
    http_req_failed:   ['rate<0.20'],   // cho phép fail cao hơn trong spike
    http_req_duration: ['p(95)<3000'],
  },
};

/** Breakpoint test — tăng dần để xác định throughput tối đa */
export const BREAKPOINT = {
  stages: [
    { duration: '2m', target: 50  },
    { duration: '2m', target: 100 },
    { duration: '2m', target: 200 },
    { duration: '2m', target: 300 },
    { duration: '2m', target: 400 },
    { duration: '2m', target: 500 },
    { duration: '1m', target: 0   },
  ],
  thresholds: {
    http_req_failed:   ['rate<0.10'],
    http_req_duration: ['p(99)<5000'],
  },
};
