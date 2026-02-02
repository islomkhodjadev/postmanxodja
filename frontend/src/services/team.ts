import type { Team, TeamMember, TeamInvite } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const getAuthHeaders = () => {
  const token = localStorage.getItem('access_token');
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
};

export const getTeams = async (): Promise<Team[]> => {
  const response = await fetch(`${API_BASE_URL}/teams`, {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get teams');
  }

  return response.json();
};

export const createTeam = async (name: string): Promise<Team> => {
  const response = await fetch(`${API_BASE_URL}/teams`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ name }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || 'Failed to create team');
  }

  return response.json();
};

export const getTeam = async (teamId: number): Promise<Team> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}`, {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get team');
  }

  return response.json();
};

export const updateTeam = async (teamId: number, name: string): Promise<Team> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify({ name }),
  });

  if (!response.ok) {
    throw new Error('Failed to update team');
  }

  return response.json();
};

export const deleteTeam = async (teamId: number): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to delete team');
  }
};

export const getTeamMembers = async (teamId: number): Promise<TeamMember[]> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}/members`, {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get team members');
  }

  return response.json();
};

export const removeTeamMember = async (teamId: number, userId: number): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}/members/${userId}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to remove team member');
  }
};

export const leaveTeam = async (teamId: number): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}/leave`, {
    method: 'POST',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to leave team');
  }
};

export const createInvite = async (teamId: number, email: string): Promise<TeamInvite> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}/invites`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ email }),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || 'Failed to create invite');
  }

  return response.json();
};

export const getTeamInvites = async (teamId: number): Promise<TeamInvite[]> => {
  const response = await fetch(`${API_BASE_URL}/teams/${teamId}/invites`, {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get team invites');
  }

  return response.json();
};

export const getUserInvites = async (): Promise<TeamInvite[]> => {
  const response = await fetch(`${API_BASE_URL}/invites`, {
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to get invites');
  }

  return response.json();
};

export const acceptInvite = async (token: string): Promise<{ message: string; team: Team }> => {
  const response = await fetch(`${API_BASE_URL}/invites/${token}/accept`, {
    method: 'POST',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.error || 'Failed to accept invite');
  }

  return response.json();
};

export const declineInvite = async (token: string): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}/invites/${token}/decline`, {
    method: 'POST',
    headers: getAuthHeaders(),
  });

  if (!response.ok) {
    throw new Error('Failed to decline invite');
  }
};
