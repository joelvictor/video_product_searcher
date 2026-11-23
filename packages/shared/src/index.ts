// Tipos e cliente HTTP do painel. Espelham os DTOs do backend Quarkus (veja /q/openapi).

export type SearchMode = "AUTO" | "MANUAL";
export type SearchStatus = "RECEIVED" | "DOWNLOADING" | "READY" | "PROCESSING" | "DONE" | "FAILED";
export type ItemStatus = "FOUND" | "NOT_FOUND";

export interface Offer {
  platformId: string;
  platformName: string;
  title: string;
  url: string;
  imageUrl: string | null;
  price: string | null;
}

export interface SearchSummary {
  id: string;
  sourceType: "UPLOAD" | "URL";
  sourceUrl: string | null;
  title: string | null;
  mode: SearchMode;
  status: SearchStatus;
  progress: string | null;
  createdAt: string;
  itemCount: number;
  foundCount: number;
  thumbnailUrl: string | null;
}

export interface SearchItem {
  id: string;
  itemNumber: number;
  origin: SearchMode;
  label: string;
  brand: string | null;
  searchQuery: string;
  relevanceLayer: "PRIMARY_FOCUS" | "SCENE_ITEM";
  detectionType: "EXACT_BRANDED_PRODUCT" | "VISUAL_FASHION_ITEM";
  category: string | null;
  confidence: number;
  timestamps: number[];
  frameUrl: string | null;
  cropUrl: string | null;
  status: ItemStatus;
  platformName: string | null;
  productTitle: string | null;
  productImageUrl: string | null;
  priceDisplay: string | null;
  originalUrl: string | null;
  shortUrl: string | null;
  affiliateUrl: string | null;
  alternatives: Offer[];
  note: string | null;
}

export interface SearchDetail {
  id: string;
  sourceType: "UPLOAD" | "URL";
  sourceUrl: string | null;
  title: string | null;
  caption: string | null;
  mode: SearchMode;
  status: SearchStatus;
  progress: string | null;
  errorMessage: string | null;
  durationSeconds: number | null;
  framesSampled: number | null;
  framesAnalyzed: number | null;
  transcript: string | null;
  videoUrl: string | null;
  mediaDeleted: boolean;
  createdAt: string;
  items: SearchItem[];
}

/** Quadro escolhido no modo manual; box = [ymin, xmin, ymax, xmax] em 0..1000. */
export interface ManualFrame {
  time: number;
  box?: [number, number, number, number] | null;
}

export interface CredentialField {
  field: string;
  label: string;
  required: boolean;
}

export interface Store {
  id: string;
  slug: string;
  displayName: string;
  domainRegex: string;
  searchDomain: string | null;
  affiliateUrlTemplate: string;
  requiredCredentialFields: CredentialField[];
  extraParams: Record<string, string>;
  androidIntentPackage: string | null;
  isActive: boolean;
  values: Record<string, string>;
  enabled: boolean;
  priority: number;
  configured: boolean;
}

export interface StoreInput {
  slug: string;
  displayName: string;
  domainRegex: string;
  searchDomain?: string | null;
  affiliateUrlTemplate: string;
  requiredCredentialFields: CredentialField[];
  extraParams: Record<string, string>;
  androidIntentPackage?: string | null;
  isActive: boolean;
}

export interface CredentialInput {
  values: Record<string, string>;
  enabled: boolean;
  priority: number;
}

export interface CreatedLink {
  shortUrl: string;
  affiliateUrl: string;
  platform: string;
}

export interface AppConfig {
  aiProvider: string;
  searchProvider: string;
  transcription: string;
  socialDomains: string[];
  maxDurationSeconds: number;
  maxManualFrames: number;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function createApiClient(baseUrl: string, getPassword: () => string | null) {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    const password = getPassword();
    if (password) headers.set("Authorization", `Bearer ${password}`);
    if (init.body && !(init.body instanceof FormData)) headers.set("Content-Type", "application/json");
    const res = await fetch(baseUrl + path, { ...init, headers });
    if (!res.ok) {
      let message = `Erro ${res.status}`;
      try {
        const body = await res.json();
        if (body?.error) message = body.error;
      } catch {
        /* corpo sem JSON */
      }
      throw new ApiError(res.status, message);
    }
    if (res.status === 204 || res.status === 202) return undefined as T;
    return res.json() as Promise<T>;
  }

  return {
    config: () => request<AppConfig>("/api/config"),
    searches: () => request<SearchSummary[]>("/api/searches"),
    search: (id: string) => request<SearchDetail>(`/api/searches/${id}`),
    searchFromUrl: (url: string, mode: SearchMode) =>
      request<{ id: string }>("/api/searches/url", { method: "POST", body: JSON.stringify({ url, mode }) }),
    searchFromUpload: (file: File, mode: SearchMode) => {
      const form = new FormData();
      form.append("file", file);
      form.append("mode", mode);
      return request<{ id: string }>("/api/searches", { method: "POST", body: form });
    },
    analyzeFrames: (id: string, frames: ManualFrame[]) =>
      request<void>(`/api/searches/${id}/frames`, { method: "POST", body: JSON.stringify({ frames }) }),
    switchOffer: (id: string, itemId: string, index: number) =>
      request<SearchItem>(`/api/searches/${id}/items/${itemId}/offer`, { method: "POST", body: JSON.stringify({ index }) }),
    deleteSearch: (id: string) => request<void>(`/api/searches/${id}`, { method: "DELETE" }),
    stores: () => request<Store[]>("/api/stores"),
    saveCredential: (id: string, input: CredentialInput) =>
      request<Store>(`/api/stores/${id}/credential`, { method: "PUT", body: JSON.stringify(input) }),
    createStore: (input: StoreInput) => request<Store>("/api/stores", { method: "POST", body: JSON.stringify(input) }),
    updateStore: (id: string, input: StoreInput) =>
      request<Store>(`/api/stores/${id}`, { method: "PUT", body: JSON.stringify(input) }),
    createLink: (url: string) => request<CreatedLink>("/api/links", { method: "POST", body: JSON.stringify({ url }) }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
